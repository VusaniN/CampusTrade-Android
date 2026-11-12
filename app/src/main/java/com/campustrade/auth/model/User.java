package com.campustrade.auth.model;

import com.google.firebase.Timestamp;

// One student profile.
public class User {

    private String stuName = "";
    private String stuEmail = "";
    private String studentNumber = "";
    private String course = "";
    private String year = "";
    private double rating = 0.0;
    // Admin flag for staff; the app also checks the build and passcode.
    private boolean isAdmin = false;
    private Timestamp createdAt = null;
    // The same ID as the Firebase login and profile.
    private String uid = "";
    private String profileImage = "";
    private String accountStatus = "active";
    private long lastActive = 0;

    public User() {
    }

    public User(String stuName, String stuEmail, String studentNumber, String course,
                double rating, boolean isAdmin, Timestamp createdAt, String uid) {
        this.stuName = stuName;
        this.stuEmail = stuEmail;
        this.studentNumber = studentNumber;
        this.course = course;
        this.rating = rating;
        this.isAdmin = isAdmin;
        this.createdAt = createdAt;
        this.uid = uid;
    }

    public String getStuName() {
        return stuName;
    }

    public void setStuName(String stuName) {
        this.stuName = stuName;
    }

    public String getStuEmail() {
        return stuEmail;
    }

    public void setStuEmail(String stuEmail) {
        this.stuEmail = stuEmail;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public void setStudentNumber(String studentNumber) {
        this.studentNumber = studentNumber;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public boolean getIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(boolean admin) {
        isAdmin = admin;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public long getLastActive() {
        return lastActive;
    }

    public void setLastActive(long lastActive) {
        this.lastActive = lastActive;
    }
}