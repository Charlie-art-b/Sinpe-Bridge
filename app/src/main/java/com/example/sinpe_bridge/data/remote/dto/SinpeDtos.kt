package com.example.sinpe_bridge.data.remote.dto

data class VoucherCreateDto(
    val code: String,
    val voucherDate: String,
    val amount: Int,
    val details: String,
    val hour: String,
    val sender: String,
    val receiver: String
)

data class VoucherResponseDto(
    val isSuccess: Boolean,
    val value: VoucherResponseValueDto? = null
)

data class VoucherResponseValueDto(
    val id: Int,
    val code: String,
    val voucherDate: String,
    val isActive: Boolean,
    val amount: Int,
    val details: String,
    val hour: String,
    val sender: String,
    val receiver: String
)