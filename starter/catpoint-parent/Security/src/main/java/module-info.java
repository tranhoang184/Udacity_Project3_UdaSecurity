module Security {
    requires java.desktop;
    requires java.prefs;
    requires java.datatransfer;
    requires Image;
    requires miglayout;
    requires com.google.common;
    requires com.google.gson;
    opens com.udacity.catpoint.security.data to com.google.gson;
}