package com.campustrade.auth;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.util.Base64;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

/** Creates and emails the invoice for a finalised collection. */
public final class InvoiceMailer {

    private InvoiceMailer() { }

    public static String newNumber(MainActivity.TransactionRecord order) {
        String suffix = order.id == null ? "" : order.id.replaceAll("[^A-Za-z0-9]", "");
        if (suffix.length() > 8) suffix = suffix.substring(suffix.length() - 8);
        return "CT-INV-" + new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date())
                + "-" + (suffix.isEmpty() ? String.valueOf(System.currentTimeMillis() % 100000) : suffix.toUpperCase(Locale.ROOT));
    }

    /** Called when the seller verifies collection. */
    public static void finalise(MainActivity host, MainActivity.TransactionRecord order) {
        if (order == null || !order.status.equals(MainActivity.TransactionRecord.COMPLETED)) return;
        if (order.invoiceNumber == null || order.invoiceNumber.isEmpty()) {
            order.invoiceNumber = newNumber(order);
        }
        if (order.invoiceFinalizedAt <= 0) order.invoiceFinalizedAt = System.currentTimeMillis();
        host.order.saveTransactions();
        sendInvoiceAutomatically(host, order);
    }

    /** Emails the invoice to both students through the Gmail Apps Script relay. */
    private static void sendInvoiceAutomatically(MainActivity host, MainActivity.TransactionRecord order) {
        File pdf = createPdf(host, order);
        if (pdf == null) {
            emailInvoice(host, order);
            return;
        }
        byte[] bytes = readBytes(pdf);
        if (bytes == null || bytes.length == 0) {
            emailInvoice(host, order);
            return;
        }
        final String pdfBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
        final String subject = "CampusTrade invoice " + order.invoiceNumber + " · " + order.itemTitle;
        final String body = emailBody(host, order);
        new Thread(() -> {
            String idToken = com.campustrade.auth.data.RelaySecurity.firebaseIdToken();
            if (idToken.isEmpty()) {
                host.runOnUiThread(() -> emailInvoice(host, order));
                return;
            }
            int code = 0;
            String response = "";
            HttpURLConnection connection = null;
            try {
                JSONObject payload = new JSONObject();
                payload.put("orderId", order.id);
                payload.put("buyerEmail", order.buyerEmail);
                payload.put("sellerEmail", order.sellerEmail);
                payload.put("invoiceNumber", order.invoiceNumber);
                payload.put("subject", subject);
                payload.put("body", body);
                payload.put("filename", "invoice-" + order.invoiceNumber.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf");
                payload.put("pdfBase64", pdfBase64);
                byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);

                connection = (HttpURLConnection) new URL(Constants.INVOICE_EMAIL_ENDPOINT).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + idToken);
                String appCheck = com.campustrade.auth.data.RelaySecurity.appCheckToken();
                if (!appCheck.isEmpty()) {
                    connection.setRequestProperty("X-Firebase-AppCheck", appCheck);
                }
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "text/plain");
                connection.setDoOutput(true);
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(40000);
                connection.setInstanceFollowRedirects(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(data);
                }
                code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400
                        ? connection.getInputStream() : connection.getErrorStream();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        StringBuilder text = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) text.append(line);
                        response = text.toString();
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("CampusTrade", "invoice email failed: " + e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
            final int resultCode = code;
            final String resultText = response;
            host.runOnUiThread(() -> {
                if (host.isDestroyed() || host.isFinishing()) return;
                boolean sent = resultCode >= 200 && resultCode < 400
                        && resultText.toLowerCase(Locale.ROOT).contains("sent");
                if (sent) {
                    Toast.makeText(host, "Invoice emailed to buyer and seller.", Toast.LENGTH_LONG).show();
                    host.notification.addNotification(host.currentStudentEmail, "Invoice emailed",
                            order.invoiceNumber + " was emailed to both students.", "payment", "", order.id);
                } else {
                    android.util.Log.w("CampusTrade", "invoice relay returned " + resultCode + ": " + resultText);
                    Toast.makeText(host, "Automatic invoice email failed. Opening your email app instead.",
                            Toast.LENGTH_LONG).show();
                    emailInvoice(host, order);
                }
            });
        }).start();
    }

    private static byte[] readBytes(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count <= 0) break;
                offset += count;
            }
            return bytes;
        } catch (Exception e) {
            return null;
        }
    }

    /** Opens the device email composer with a PDF invoice attached. */
    public static void emailInvoice(MainActivity host, MainActivity.TransactionRecord order) {
        if (order == null || !order.status.equals(MainActivity.TransactionRecord.COMPLETED)) return;
        if (order.invoiceNumber == null || order.invoiceNumber.isEmpty()) {
            order.invoiceNumber = newNumber(order);
            order.invoiceFinalizedAt = System.currentTimeMillis();
            host.order.saveTransactions();
        }
        File pdf = createPdf(host, order);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/pdf");
        send.putExtra(Intent.EXTRA_EMAIL, new String[] {order.buyerEmail, order.sellerEmail});
        send.putExtra(Intent.EXTRA_SUBJECT, "CampusTrade invoice " + order.invoiceNumber + " · " + order.itemTitle);
        send.putExtra(Intent.EXTRA_TEXT, emailBody(host, order));
        if (pdf != null) {
            try {
                Uri uri = FileProvider.getUriForFile(host, host.getPackageName() + ".fileprovider", pdf);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                android.util.Log.w("CampusTrade", "invoice attachment unavailable: " + e.getMessage());
            }
        }
        try {
            host.startActivity(Intent.createChooser(send, "Email invoice"));
            Toast.makeText(host, "Invoice " + order.invoiceNumber + " is ready in your email app.",
                    Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException e) {
            host.ui.showSimpleMessage("Invoice " + order.invoiceNumber,
                    "No email app is available. The invoice is saved in CampusTrade and can be shared later.");
        }
    }

    private static String emailBody(MainActivity host, MainActivity.TransactionRecord order) {
        return "CampusTrade invoice\n\n"
                + "Invoice: " + order.invoiceNumber + "\n"
                + "Order: " + order.id + "\n"
                + "Item: " + order.itemTitle + "\n"
                + "Buyer: " + order.buyerName + " (" + order.buyerEmail + ")\n"
                + "Seller: " + host.studentName(order.sellerEmail) + " (" + order.sellerEmail + ")\n"
                + "Amount: " + Order.rand(order.value()) + "\n"
                + "Payment method: " + order.method + "\n"
                + "Fulfilment: " + order.deliveryMethod
                + (order.deliveryOrder() ? " · " + order.deliveryAddress : "") + "\n"
                + "Collection verified: " + new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                .format(new Date(order.sellerVerifiedTime > 0 ? order.sellerVerifiedTime : System.currentTimeMillis())) + "\n\n"
                + "CampusTrade does not process payments or refunds. Payment is settled directly between the two students. "
                + "This invoice is a record of the finalised sale.\n";
    }

    private static File createPdf(MainActivity host, MainActivity.TransactionRecord order) {
        File folder = new File(host.getCacheDir(), "invoices");
        if (!folder.exists() && !folder.mkdirs()) return null;
        File file = new File(folder, "invoice-" + order.invoiceNumber.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf");
        PdfDocument document = new PdfDocument();
        try {
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = document.startPage(info);
            Canvas canvas = page.getCanvas();
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            int left = 48;
            int y = 68;
            paint.setColor(Color.rgb(23, 111, 84));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(24);
            canvas.drawText("CampusTrade", left, y, paint);
            y += 18;
            paint.setColor(Color.DKGRAY);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(10);
            canvas.drawText("Student marketplace · invoice", left, y, paint);

            y += 46;
            paint.setColor(Color.BLACK);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(18);
            canvas.drawText("Invoice " + order.invoiceNumber, left, y, paint);
            y += 24;
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(11);
            paint.setColor(Color.DKGRAY);
            String finalised = order.invoiceFinalizedAt > 0
                    ? new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(order.invoiceFinalizedAt))
                    : new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());
            canvas.drawText("Finalised: " + finalised, left, y, paint);
            y += 16;
            canvas.drawText("Order: " + order.id, left, y, paint);

            y += 38;
            paint.setColor(Color.rgb(240, 246, 243));
            canvas.drawRect(left, y - 20, 547, y + 156, paint);
            paint.setColor(Color.BLACK);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(13);
            canvas.drawText(order.itemTitle, left + 16, y, paint);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(11);
            y += 24;
            canvas.drawText("Buyer: " + order.buyerName + "  <" + order.buyerEmail + ">", left + 16, y, paint);
            y += 18;
            canvas.drawText("Seller: " + host.studentName(order.sellerEmail) + "  <" + order.sellerEmail + ">", left + 16, y, paint);
            y += 18;
            canvas.drawText("Payment: " + order.method + " (settled directly between students)", left + 16, y, paint);
            y += 18;
            canvas.drawText("Fulfilment: " + order.deliveryMethod, left + 16, y, paint);
            if (order.deliveryOrder() && !order.deliveryAddress.isEmpty()) {
                y += 18;
                canvas.drawText("Address: " + order.deliveryAddress, left + 16, y, paint);
            }
            y += 22;
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(16);
            canvas.drawText("Total: " + Order.rand(order.value()), left + 16, y, paint);

            y += 50;
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(10);
            paint.setColor(Color.DKGRAY);
            canvas.drawText("Collection was verified by the seller in CampusTrade.", left, y, paint);
            y += 16;
            canvas.drawText("CampusTrade does not process payments or refunds. Cash or EFT is arranged directly between students.", left, y, paint);
            y += 16;
            canvas.drawText("This invoice is a record of the finalised sale, not proof of platform payment.", left, y, paint);
            document.finishPage(page);
            try (FileOutputStream output = new FileOutputStream(file)) {
                document.writeTo(output);
            }
            return file;
        } catch (Exception e) {
            android.util.Log.w("CampusTrade", "invoice PDF failed: " + e.getMessage());
            return null;
        } finally {
            document.close();
        }
    }
}
