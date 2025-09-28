package com.qunhui.chen.fullstacktest.adapter.web.controller;

import com.qunhui.chen.fullstacktest.adapter.web.domain.CreateProductForm
import com.qunhui.chen.fullstacktest.adapter.web.domain.UpdateProductForm
import com.qunhui.chen.fullstacktest.repo.ProductRepo
import com.qunhui.chen.fullstacktest.repo.VariantRepo
import com.qunhui.chen.fullstacktest.service.product.ProductService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * - GET   "/"                         → 首页
 * - GET   "/products"                 → 返回 <tbody id="products"> 片段
 * - GET   "/products/form"            → 返回创建产品表单片段
 * - POST  "/addProducts"              → 处理创建（沿用原路由），成功后刷新 #products
 * - GET   "/products/{productId}/variants" → 返回某产品的变体行片段
 *
 * @author Qunhui Chen
 * @date 2025/9/24 00:58
 */
@Controller
class ProductController(
    private val productRepo: ProductRepo,
    private val variantRepo: VariantRepo,
    private val productService: ProductService,

    ) {

    // 首页
    @GetMapping("/")
    fun index(model: Model): String = "index"

    // 返回 <tbody id="products"> 片段（分页/搜索）
    @GetMapping("/products")
    fun productsPartial(
        @RequestParam(name = "search", required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        model: Model
    ): String {
        val total = productRepo.countForView(search)
        val totalPages = if (total == 0L) 1 else ((total + size - 1) / size).toInt()
        val p = page.coerceIn(1, totalPages)
        val offset = (p - 1) * size

        val items = productRepo.listForViewPaged(limit = size, offset = offset, search = search)

        model.addAttribute("items", items)
        model.addAttribute("page", p)
        model.addAttribute("size", size)
        model.addAttribute("total", total)
        model.addAttribute("totalPages", totalPages)
        model.addAttribute("search", search)
        return "products/_tbody :: products_tbody"
    }

    // 表单片段
    @GetMapping("/products/form")
    fun form(model: Model): String = "products/_form :: create_form"

    // 创建 & 刷新表格（沿用原路由 /addProducts，避免前端 htmx 改动）
    @PostMapping("/addProducts")
    fun create(
        @Valid @ModelAttribute form: CreateProductForm,
        result: BindingResult,
        model: Model,
        response: HttpServletResponse
    ): String {
        if (result.hasErrors()) {
            model.addAttribute("errors", result)
            model.addAttribute("form", form)
            return "products/_form :: create_form"
        }

        // 2) 前置检测：productId 是否已存在
        if (productService.existsByProductId(form.productId)) {
            result.rejectValue("productId", "duplicate", "该 Product ID 已存在")
            model.addAttribute("errors", result)
            model.addAttribute("form", form)
            response.status = 422
            return "products/_form :: create_form"
        }

        productService.addProduct(form)

        val rows = productService.listFirstPage(10)

        model.addAttribute("items", rows)
        response.setHeader("HX-Retarget", "#products")
        response.setHeader("HX-Reswap", "outerHTML")
        response.setHeader("HX-Trigger", "product:created")
        return "products/_tbody :: products_tbody"
    }

    // 变体行片段
    @GetMapping("/products/{productId}/variants")
    fun variantsPartial(@PathVariable productId: Long, model: Model): String {
        val rows = variantRepo.listByProductId(productId)
        val optionNames = productRepo.loadOptionNames(productId)
        model.addAttribute("productId", productId)
        model.addAttribute("rows", rows)
        model.addAttribute("optionNames", optionNames)
        return "products/_variants :: variants_rows"
    }

    // 新增：搜索页（带搜索框与主动搜索）
    @GetMapping("/products/search")
    fun searchPage(model: Model): String = "products/search"

    // 新增：编辑页
    @GetMapping("/products/{productId}/edit")
    fun editPage(@PathVariable productId: Long, model: Model, response: HttpServletResponse): String {
        val product = productRepo.findById(productId)
            ?: run {
                response.status = 404
                return "errors/404"
            }
        val variants = variantRepo.listByProductId(productId)
        val optionNames = productRepo.loadOptionNames(productId)

        val tagsJoined = product.tags?.joinToString(", ")

        model.addAttribute("product", product)
        model.addAttribute("variants", variants)
        model.addAttribute("optionNames", optionNames)
        model.addAttribute("tagsJoined", tagsJoined ?: "")

        return "products/edit"
    }

    // 新增：提交更新（HTMX 局部刷新）
    @PostMapping("/products/{productId}/update")
    fun updateProduct(
        @PathVariable productId: Long,
        @ModelAttribute form: UpdateProductForm,
        model: Model,
        response: HttpServletResponse
    ): String {
        if (form.productId != productId) {
            response.status = 400
            model.addAttribute("error", "Path productId 与表单不一致")
        } else {
            productService.updateProduct(form)
            response.setHeader("HX-Trigger", "product:updated")
        }

        // 重新加载最新数据以回显
        val product = productRepo.findById(productId)
        val variants = variantRepo.listByProductId(productId)
        val optionNames = productRepo.loadOptionNames(productId)
        val tagsJoined = product?.tags?.joinToString(", ")

        model.addAttribute("product", product)
        model.addAttribute("variants", variants)
        model.addAttribute("optionNames", optionNames)
        model.addAttribute("tagsJoined", tagsJoined ?: "")
        return "products/edit :: edit_form"
    }

    // 新增：删除产品（含变体）
    @DeleteMapping("/products/{productId}")
    fun deleteProduct(
        @PathVariable productId: Long,
        @RequestParam(name = "search", required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        model: Model,
        response: HttpServletResponse
    ): String {
        // 删除产品及其变体（事务内）
        productService.deleteProduct(productId)

        // 重新计算分页并返回最新表格片段
        val total = productRepo.countForView(search)
        val totalPages = if (total == 0L) 1 else ((total + size - 1) / size).toInt()
        val p = page.coerceIn(1, totalPages)
        val offset = (p - 1) * size
        val items = productRepo.listForViewPaged(limit = size, offset = offset, search = search)

        model.addAttribute("items", items)
        model.addAttribute("page", p)
        model.addAttribute("size", size)
        model.addAttribute("total", total)
        model.addAttribute("totalPages", totalPages)
        model.addAttribute("search", search)

        response.setHeader("HX-Retarget", "#products")
        response.setHeader("HX-Reswap", "outerHTML")
        response.setHeader("HX-Trigger", "product:deleted")

        return "products/_tbody :: products_tbody"
    }

    // 新增：导出 CSV（不包含 variants）
    @GetMapping("/products/export", produces = ["text/csv"])
    fun exportProductsCsv(
        @RequestParam(name = "search", required = false) search: String?,
        response: HttpServletResponse
    ) {
        response.characterEncoding = "UTF-8"
        response.contentType = "text/csv; charset=UTF-8"

        val ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        val filename = "products-$ts.csv"
        val encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20")
        response.setHeader("Content-Disposition", "attachment; filename=\"$filename\"; filename*=UTF-8''$encoded")

        val writer = response.writer
        // 表头
        writer.append("Product ID,Title,Vendor,Product Type,Tags,Min Price,Updated").append('\n')

        val total = productRepo.countForView(search)
        val pageSize = 1000
        var offset = 0
        while (offset < total) {
            val items = productRepo.listForViewPaged(limit = pageSize, offset = offset, search = search)
            if (items.isEmpty()) break
            for (item in items) {
                fun read(vararg names: String): Any? = names.asSequence().map { readProp(item, it) }.firstOrNull { it != null }

                val productId = read("productId", "externalId", "pid")
                val title = read("title", "name")
                val vendor = read("vendor", "brand")
                val productType = read("productType", "type")
                val tags = read("tags")
                val minPrice = read("minPrice", "priceMin", "lowestPrice")
                val updated = read("updatedAt", "updated", "modifiedAt", "lastUpdatedAt")

                writer
                    .append(toCsvCell(productId)).append(',')
                    .append(toCsvCell(title)).append(',')
                    .append(toCsvCell(vendor)).append(',')
                    .append(toCsvCell(productType)).append(',')
                    .append(toCsvCell(tags)).append(',')
                    .append(toCsvCell(minPrice)).append(',')
                    .append(toCsvCell(updated)).append('\n')
            }
            writer.flush()
            offset += items.size
        }
    }

    // 读取对象或 Map 的属性（安全兜底）
    private fun readProp(bean: Any?, name: String): Any? {
        if (bean == null) return null
        if (bean is Map<*, *>) return bean[name]
        return try {
            @Suppress("UNCHECKED_CAST")
            (bean::class.memberProperties.firstOrNull { it.name == name } as? KProperty1<Any, *>)?.get(bean)
        } catch (_: Exception) {
            try {
                val cls = bean.javaClass
                val getterName = "get" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                val m = cls.methods.firstOrNull { it.parameterCount == 0 && (it.name == name || it.name == getterName) }
                m?.invoke(bean)
            } catch (_: Exception) {
                null
            }
        }
    }

    // 转为 CSV 单元格并转义
    private fun toCsvCell(v: Any?): String {
        val raw = when (v) {
            null -> ""
            is Iterable<*> -> v.filterNotNull().joinToString(";")
            is Array<*> -> v.filterNotNull().joinToString(";")
            is java.math.BigDecimal -> v.stripTrailingZeros().toPlainString()
            is java.time.Instant -> java.time.OffsetDateTime.ofInstant(v, java.time.ZoneOffset.UTC).toString()
            is java.time.LocalDateTime -> v.atOffset(java.time.ZoneOffset.UTC).toString()
            is java.time.ZonedDateTime -> v.toOffsetDateTime().toString()
            else -> v.toString()
        }
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
