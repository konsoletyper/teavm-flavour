/*
 *  Copyright 2015 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.flavour.example.server;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import java.beans.PropertyVetoException;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.eclipse.persistence.jpa.PersistenceProvider;
import org.hsqldb.jdbc.JDBCDriver;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.support.SharedEntityManagerBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@PropertySource(value = { "classpath:default-config.properties", "WEB-INF/config.properties" })
@ComponentScan("org.teavm.flavour.example")
@EnableTransactionManagement
public class ApplicationConfiguration {
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public DataSource dataSource(@Value("${hsqldb.file}") String fileName) throws PropertyVetoException {
        ComboPooledDataSource dataSource = new ComboPooledDataSource();
        dataSource.setDriverClass(JDBCDriver.class.getName());
        dataSource.setJdbcUrl("jdbc:hsqldb:file:" + fileName);
        dataSource.setUser("SA");
        dataSource.setPassword("");
        return dataSource;
    }

    @Bean
    public FactoryBean<EntityManagerFactory> entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPersistenceUnitName("PU");
        emf.setPackagesToScan("org.teavm.flavour.example.model");
        emf.setPersistenceProviderClass(PersistenceProvider.class);

        Map<String, String> properties = new HashMap<>();
        properties.put("eclipselink.weaving", "false");
        properties.put("eclipselink.ddl-generation", "create-or-extend-tables");
        emf.setJpaPropertyMap(properties);

        return emf;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean
    public FactoryBean<EntityManager> entityManager(EntityManagerFactory emf) {
        SharedEntityManagerBean em = new SharedEntityManagerBean();
        em.setEntityManagerFactory(emf);
        return em;
    }
}
