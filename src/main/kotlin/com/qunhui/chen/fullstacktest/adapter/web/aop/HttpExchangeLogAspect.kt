package com.qunhui.chen.fullstacktest.adapter.web.aop

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.ui.Model
import org.springframework.ui.ModelMap
import org.springframework.validation.BindingResult
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.multipart.MultipartFile
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.lang.reflect.Parameter

@Aspect
@Component
class HttpReqRespLogAspect {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()
    private val maxLen = 2000

    // 命中所有 Web 控制器（视图或 REST）
    @Pointcut(
        "@within(org.springframework.web.bind.annotation.RestController) " +
                "|| @within(org.springframework.stereotype.Controller)"
    )
    fun anyWebController() {
    }

    // 只拦 GET / POST
    @Pointcut(
        "@annotation(org.springframework.web.bind.annotation.GetMapping) " +
                "|| @annotation(org.springframework.web.bind.annotation.PostMapping)"
    )
    fun getOrPost() {
    }

    @Around("anyWebController() && getOrPost()")
    fun around(pjp: ProceedingJoinPoint): Any? {
        val attrs = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
        val req = attrs.request
        val resp: HttpServletResponse? = attrs.response

        val method = req.method
        val uri = req.requestURI + (req.queryString?.let { "?$it" } ?: "")

        val reqArgsJson = collectBizArgsNamed(pjp)
        val start = System.nanoTime()

        return try {
            val ret = pjp.proceed()

            val (status, bodyJson) = when (ret) {
                is ResponseEntity<*> -> ret.statusCode.value() to safeJson(ret.body)
                else -> (resp?.status ?: 200) to safeJson(ret)
            }

            val costMs = (System.nanoTime() - start) / 1_000_000.0
            log.info(
                "HTTP {} {} | status={} | {} ms | req={} | resp={}",
                method, uri, status, "%.2f".format(costMs), reqArgsJson, cut(bodyJson)
            )
            ret
        } catch (e: Throwable) {
            val costMs = (System.nanoTime() - start) / 1_000_000.0
            val status = resp?.status ?: 500
            log.warn(
                "HTTP {} {} | status={} | {} ms | req={} | ex",
                method, uri, status, "%.2f".format(costMs), reqArgsJson, e
            )
            throw e
        }
    }

    /** 仅收集“业务实参”，排除 Servlet/MVC 框架对象，避免触发输出流/写入器等副作用 */
    private fun collectBizArgsNamed(pjp: ProceedingJoinPoint): String {
        val sig = pjp.signature as MethodSignature
        val args = pjp.args
        val javaParams: Array<Parameter> = sig.method.parameters
        val names: Array<String?> = sig.parameterNames // 对 Kotlin 控制器可直接拿到名字

        val pairs = mutableListOf<String>()
        for (i in args.indices) {
            val v = args[i]
            if (isFrameworkArg(v)) continue

            // 1) 先取注解名（优先 value/name）
            val p = javaParams[i]
            var name: String? = null
            p.getAnnotation(RequestParam::class.java)?.let {
                name = it.value.takeIf { s -> s.isNotBlank() } ?: it.name.takeIf { s -> s.isNotBlank() }
            }
            if (name == null) {
                p.getAnnotation(PathVariable::class.java)?.let {
                    name = it.value.takeIf { s -> s.isNotBlank() } ?: it.name.takeIf { s -> s.isNotBlank() }
                }
            }
            // 2) 再退回到方法参数名；最后兜底 arg{i}
            val finalName = name ?: names.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "arg$i"

            pairs += "$finalName=${cut(safeJson(v))}"
        }
        return "{${pairs.joinToString(", ")}}"
    }

    private fun isFrameworkArg(x: Any?): Boolean = when (x) {
        null -> false
        is ServletRequest, is ServletResponse -> true
        is java.io.InputStream, is java.io.OutputStream -> true
        is Model, is ModelMap -> true
        is BindingResult -> true
        is MultipartFile -> true
        is HttpSession -> true
        is java.security.Principal -> true
        is java.util.Locale -> true
        else -> false
    }

    private fun safeJson(v: Any?): String =
        try {
            mapper.writeValueAsString(v)
        } catch (_: Exception) {
            v?.toString() ?: "null"
        }

    private fun cut(s: String?, max: Int = maxLen): String =
        when {
            s == null -> "null"
            s.length > max -> s.take(max) + "…(${s.length})"
            else -> s
        }
}
