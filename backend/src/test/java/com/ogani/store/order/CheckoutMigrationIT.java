package com.ogani.store.order;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class CheckoutMigrationIT {
    @Test void migrationPreservesMaximumLengthLegacyNames() throws Exception {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/ogani_store_test");
        assertTrue(url.endsWith("_test"),"Migration fixtures require a dedicated test database");
        String user=System.getenv().getOrDefault("TEST_DB_USER","postgres");
        String password=System.getenv().getOrDefault("TEST_DB_PASSWORD",System.getenv().getOrDefault("DB_PASSWORD","postgres"));
        String schema="checkout_migration_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(url,user,password)) {
            try {
                Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                        .locations("classpath:db/migration").target("7").load().migrate();
                try(var insert=connection.prepareStatement("insert into "+schema+".orders(order_code,user_id,first_name,last_name,email,phone,ship_country,ship_address,ship_city,subtotal,total) values('MAX-NAMES',3,?,?,'buyer@example.test','0901234567','Vietnam','12 Test','HCM',1,1)")) {
                    insert.setString(1,"F".repeat(60));insert.setString(2,"L".repeat(60));insert.executeUpdate();
                }
                Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                        .locations("classpath:db/migration").load().migrate();
                try(var query=connection.createStatement(); var result=query.executeQuery("select ship_recipient_name from "+schema+".orders where order_code='MAX-NAMES'")) {
                    assertTrue(result.next());assertEquals("F".repeat(60)+" "+"L".repeat(60),result.getString(1));
                }
            } finally {
                // Only the uniquely named fixture schema created by this test is removed.
                try(var cleanup=connection.createStatement()) { cleanup.execute("drop schema if exists "+schema+" cascade"); }
            }
        }
    }
}
