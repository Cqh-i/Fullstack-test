package com.qunhui.chen.fullstacktest.common

/**
 * @author Qunhui Chen
 * @date 2025/9/26 11:19
 */
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import java.net.*
import java.net.http.HttpClient
import java.time.Duration

@ConfigurationProperties("http.client")
data class HttpClientProps(
    var connectTimeout: Duration = Duration.ofSeconds(10),
    var requestTimeout: Duration = Duration.ofSeconds(20),
    var followRedirects: HttpClient.Redirect = HttpClient.Redirect.NORMAL,
    var httpVersion: HttpClient.Version = HttpClient.Version.HTTP_2,
    var proxyHost: String? = null,
    var proxyPort: Int? = null
)

@Configuration
@EnableConfigurationProperties(HttpClientProps::class)
class HttpClientConfig {
    @Bean
    fun httpClient(props: HttpClientProps): HttpClient {
        val builder = HttpClient.newBuilder()
            .connectTimeout(props.connectTimeout)
            .followRedirects(props.followRedirects)
            .version(props.httpVersion)

        if (!props.proxyHost.isNullOrBlank() && props.proxyPort != null) {
            builder.proxy(ProxySelector.of(InetSocketAddress(props.proxyHost, props.proxyPort!!)))
        }
        return builder.build()
    }
}
