package com.qunhui.chen.fullstacktest.adapter.web.controller


import com.fasterxml.jackson.databind.ObjectMapper
import com.qunhui.chen.fullstacktest.repo.ProductRepo
import com.qunhui.chen.fullstacktest.repo.ProductUpsertCmd
import com.qunhui.chen.fullstacktest.repo.VariantRepo
import com.qunhui.chen.fullstacktest.repo.VariantUpsertCmd
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import java.math.BigDecimal
import java.time.OffsetDateTime
import org.springframework.transaction.support.TransactionTemplate


/**
 * @author Qunhui Chen
 * @date 2025/9/25 11:34
 */
// DTO —— 与表单字段一一对应
data class CreateVariantForm(
    @field:NotNull(message = "Variant ID cannot be null")
    val variantId: Long,
    val sku: String? = null,

    @field:NotNull(message = "Price cannot be null")
    @field:DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    val price: BigDecimal? = null,

    @field:DecimalMin(value = "0.0", inclusive = false, message = "Compare At Price must be greater than 0")
    val comparePrice: BigDecimal? = null,
    val available: Boolean? = null,
    val imageUrl: String? = null,
    val option1: String? = null,
    val option2: String? = null,
    val option3: String? = null
)

data class CreateProductForm(
    @field:NotNull(message = "Product ID cannot be null")
    val productId: Long,

    @field:NotBlank(message = "Title cannot be blank")
    val title: String,
    val vendor: String? = null,
    val productType: String? = null,
    val tagsText: String? = null,
    val optionNames: List<String> = emptyList(),

    @field:NotEmpty(message = "At least one variant is required")
    @field:Valid
    val variants: List<CreateVariantForm> = emptyList()
)

@Controller
class ProductPageController(
    private val productRepo: ProductRepo,
    private val variantRepo: VariantRepo,
    private val mapper: ObjectMapper,
    private val txTemplate: TransactionTemplate

) {
    // 表单片段
    @GetMapping("/products/form")
    fun form(model: Model): String = "products/_form :: create_form"

    // 创建 & 刷新表格
    @PostMapping("/addProducts")
    fun create(
        @Valid @ModelAttribute form: CreateProductForm,
        result: BindingResult,
        model: Model
    ): String {
        // A. 表单校验
        if (result.hasErrors()) {
            model.addAttribute("errors", result)
            model.addAttribute("form", form)
            return "products/_form :: create_form"
        }

        // B. 组装 options_json（名称 + 去重值集）
        val tags: List<String>? = form.tagsText
            ?.split(',', '，', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }

        data class Opt(val name: String, val position: Int, val values: List<String>)

        val optNames = form.optionNames.mapIndexedNotNull { i, n ->
            val name = (n ?: "").trim()
            if (name.isEmpty()) null else i to name
        }
        val optionsJson: String? =
            if (optNames.isEmpty()) null
            else {
                val values1 = form.variants.mapNotNull { it.option1?.trim() }.distinct()
                val values2 = form.variants.mapNotNull { it.option2?.trim() }.distinct()
                val values3 = form.variants.mapNotNull { it.option3?.trim() }.distinct()
                val list = mutableListOf<Opt>()
                optNames.forEach { (i, name) ->
                    val vals = when (i) {
                        0 -> values1; 1 -> values2; else -> values3
                    }
                    list += Opt(name, i + 1, vals)
                }
                mapper.writeValueAsString(list)
            }

        txTemplate.executeWithoutResult {
            val now = OffsetDateTime.now()
            productRepo.upsert(
                ProductUpsertCmd(
                    productId = form.productId,
                    title = form.title,
                    vendor = form.vendor,
                    productType = form.productType,
                    tags = tags,
                    optionsJson = optionsJson,
                    createdAt = now,
                    updatedAt = now
                )
            )
            form.variants.forEach { v ->
                variantRepo.upsert(
                    VariantUpsertCmd(
                        variantId = v.variantId,
                        productId = form.productId,
                        sku = v.sku,
                        imageUrl = v.imageUrl,
                        price = v.price,
                        comparePrice = v.comparePrice,
                        available = v.available,
                        position = null,
                        option1 = v.option1, option2 = v.option2, option3 = v.option3,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }

        // 重新查询并返回 tbody 片段
        val rows = productRepo.listForViewPaged(limit = 10, offset = 0, search = null)
        model.addAttribute("items", rows)
        return "products/_tbody :: products_tbody"
    }
}
