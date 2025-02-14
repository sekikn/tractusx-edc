
plugins {
    `java-library`
    `maven-publish`
    id("io.swagger.core.v3.swagger-gradle-plugin")
}

dependencies {
    implementation(edc.spi.core)
    implementation(edc.spi.policy)
    implementation(edc.api.management)
    implementation(edc.spi.catalog)
    implementation(edc.spi.transactionspi)
    implementation(edc.spi.transaction.datasource)
    implementation(edc.ids)
    implementation(edc.sql.core)
    implementation(edc.sql.lease)
    implementation(edc.sql.pool)


    implementation(libs.postgres)
    implementation(libs.jakarta.rsApi)


    implementation(edc.spi.aggregateservices)
    testImplementation(libs.awaitility)
}
