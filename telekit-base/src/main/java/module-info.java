import org.telekit.base.plugin.Plugin;

module telekit.base {

    uses Plugin;

    // modularized dependencies
    requires transitive java.desktop;
    requires transitive java.inject;
    requires transitive java.sql;
    requires transitive java.xml;
    requires transitive java.prefs;

    requires transitive javafx.controls;
    requires transitive javafx.fxml;

    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.core;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.dataformat.javaprop;
    requires transitive com.fasterxml.jackson.dataformat.xml;
    requires transitive com.fasterxml.jackson.dataformat.yaml;

    requires transitive com.ctc.wstx;
    requires transitive de.skuzzle.semantic;
    requires transitive inet.ipaddr;
    requires transitive org.hsqldb;
    requires transitive org.jetbrains.annotations;
    requires transitive org.bouncycastle.pkix;
    requires transitive org.bouncycastle.provider;

    // dependencies with Automatic-Module-Name
    requires org.apache.commons.codec;
    requires org.apache.commons.collections4;
    requires org.apache.commons.compress;
    requires org.apache.commons.lang3;
    requires org.apache.commons.net;
    requires org.apache.httpcomponents.core5.httpcore5;
    requires org.apache.httpcomponents.core5.httpcore5.h2;
    requires org.apache.httpcomponents.client5.httpclient5;
    requires org.jsoup;
    requires org.yaml.snakeyaml;

    // dependencies without Auto-Module-Name
    requires expectit.core;
    requires commons.dbutils;
    requires j2html;
    requires sshj;

    // exports
    exports org.telekit.base;
    exports org.telekit.base.desktop;
    exports org.telekit.base.desktop.mvvm;
    exports org.telekit.base.desktop.routing;
    exports org.telekit.base.di;
    exports org.telekit.base.domain;
    exports org.telekit.base.domain.event;
    exports org.telekit.base.domain.exception;
    exports org.telekit.base.domain.security;
    exports org.telekit.base.event;
    exports org.telekit.base.i18n;
    exports org.telekit.base.net;
    exports org.telekit.base.net.connection;
    exports org.telekit.base.plugin;
    exports org.telekit.base.plugin.internal to telekit.desktop;
    exports org.telekit.base.preferences;
    exports org.telekit.base.preferences.internal to telekit.desktop, com.fasterxml.jackson.databind;
    exports org.telekit.base.service;
    exports org.telekit.base.service.impl;
    exports org.telekit.base.telecom;
    exports org.telekit.base.telecom.ip;
    exports org.telekit.base.util;

    opens org.telekit.base.domain to com.fasterxml.jackson.databind;
    opens org.telekit.base.domain.security to com.fasterxml.jackson.databind;
    opens org.telekit.base.preferences.internal to com.fasterxml.jackson.databind;
}