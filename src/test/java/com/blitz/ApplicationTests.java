package com.blitz;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsWithH2SessionAndThymeleaf() throws Exception {
        assertNotNull(applicationContext.getBean(SpringTemplateEngine.class));

        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:blitz"));

            try (ResultSet tables = connection.getMetaData()
                    .getTables(null, null, "SPRING_SESSION", new String[] {"TABLE"})) {
                assertTrue(tables.next());
            }
        }
    }
}
