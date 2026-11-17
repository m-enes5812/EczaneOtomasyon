package com.eczane.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Günlükler veri klasöründeki logs/ altına, dönüşümlü dosyalara yazılır (5 × 2 MB). */
public final class Logging {

    private Logging() {
    }

    public static void init(AppPaths paths) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT [%4$s] %3$s — %5$s%6$s%n");
        LogManager.getLogManager().reset();
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);
        try {
            FileHandler file = new FileHandler(paths.logs().resolve("eczane-%g.log").toString(), 2_000_000, 5, true);
            file.setEncoding(StandardCharsets.UTF_8.name());
            file.setFormatter(new SimpleFormatter());
            file.setLevel(Level.INFO);
            root.addHandler(file);
        } catch (IOException e) {
            System.err.println("Günlük dosyası açılamadı: " + e.getMessage());
        }
        Handler console = new ConsoleHandler();
        console.setLevel(Level.WARNING);
        root.addHandler(console);
    }
}
