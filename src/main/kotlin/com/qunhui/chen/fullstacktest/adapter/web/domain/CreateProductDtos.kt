package com.qunhui.chen.fullstacktest.adapter.web.domain

/**
 * @author Qunhui Chen
 * @date 2025/9/26 15:58
 */

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

/**
 * DTO for product create form
 */
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

    // Up to 3 option names; non-empty names must be distinct
    val optionNames: List<String> = emptyList(),

    @field:NotEmpty(message = "At least one variant is required")
    @field:Valid
    val variants: List<CreateVariantForm> = emptyList()
) {
    @AssertTrue(message = "Option names must be distinct")
    fun distinctOptionNames(): Boolean {
        val names = optionNames.mapNotNull { it?.trim() }.filter { it.isNotEmpty() }
        return names.size == names.distinct().size
    }
}
