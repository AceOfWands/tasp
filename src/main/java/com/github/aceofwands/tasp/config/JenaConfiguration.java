package com.github.aceofwands.tasp.config;

import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2Factory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JenaConfiguration {

    // Una location vuota crea un dataset TDB2 in memoria (utile nei test)
    @Bean(destroyMethod = "close")
    public Dataset knowledgeBase(@Value("${tasp.tdb.location:}") String location) {
        return location == null || location.isBlank()
                ? TDB2Factory.createDataset()
                : TDB2Factory.connectDataset(location);
    }
}