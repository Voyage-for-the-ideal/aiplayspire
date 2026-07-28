package com.megacrit.cardcrawl.core;

import java.io.IOException;
import java.io.Reader;
import java.util.Properties;

class BuildSettings {
    private final Properties prop;
    public static final String defaultFilename = "build.properties";

    BuildSettings(Reader reader) throws IOException {
        this.prop = new Properties();
        this.prop.load(reader);
    }

    String getDistributor() throws BuildSettingsException {
        String distributor = this.prop.getProperty("distributor");
        if (distributor != null) {
            return distributor;
        }
        throw new BuildSettingsException("The key 'distributor' is null in file=build.properties");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\core\BuildSettings
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

