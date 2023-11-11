module Image {
    requires software.amazon.awssdk.services.rekognition;
    requires software.amazon.awssdk.auth;
    requires org.slf4j;
    requires software.amazon.awssdk.core;
    requires java.desktop;
    requires software.amazon.awssdk.regions;
    exports com.udacity.catpoint.image.service;
}
