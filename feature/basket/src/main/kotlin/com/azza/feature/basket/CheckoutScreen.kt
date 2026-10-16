package com.azza.feature.basket

import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azza.core.common.AppError
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Money
import com.azza.core.ui.LoadingState
import com.azza.core.ui.discounted
import com.azza.core.ui.errorMessage
import com.azza.core.ui.format

@Composable
fun CheckoutScreen(
    onBackToCart: () -> Unit,
    onViewOrder: (String) -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()

    state.placed?.let { placed ->
        return Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.checkout_placed_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                if (state.paid) stringResource(R.string.order_heading, placed.reference)
                else stringResource(R.string.checkout_placed_body, placed.reference),
                color = AzzaTheme.colors.textSecondary,
            )
            Text(placed.total.format(), style = MaterialTheme.typography.titleLarge)
            if (state.paid) {
                Text(stringResource(R.string.checkout_paid), color = AzzaTheme.colors.success)
            } else {
                Text(stringResource(R.string.order_test_payment_notice), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::payNow, enabled = !state.paying, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(if (state.paying) R.string.order_test_paying else R.string.order_test_pay))
                }
                state.payError?.let { Text(payErrorText(it), color = AzzaTheme.colors.error) }
            }
            OutlinedButton(onClick = { onViewOrder(placed.orderId) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.checkout_view_order))
            }
        }
    }

    val currentCart = cart ?: return LoadingState()
    val subtotal: Money? = currentCart.subtotal()

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.checkout_heading), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.checkout_subheading), color = AzzaTheme.colors.textSecondary)

        val saved by viewModel.savedAddresses.collectAsStateWithLifecycle()
        if (saved.isNotEmpty()) {
            Text(stringResource(R.string.checkout_saved_addresses), style = MaterialTheme.typography.labelMedium, color = AzzaTheme.colors.textSecondary)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                saved.forEach { a ->
                    FilterChip(
                        selected = state.address == a.oneLine,
                        onClick = { viewModel.useAddress(a) },
                        label = { Text(a.label?.takeIf { it.isNotBlank() } ?: a.recipientName) },
                    )
                }
            }
        }
        Field(state.fullName, { viewModel.edit { copy(fullName = it) } }, R.string.checkout_full_name, capitalize = true)
        Field(state.phone, { viewModel.edit { copy(phone = it) } }, R.string.checkout_phone, KeyboardType.Phone)
        Field(state.address, { viewModel.edit { copy(address = it) } }, R.string.checkout_address, singleLine = false)
        Field(state.postalCode, { viewModel.edit { copy(postalCode = it) } }, R.string.checkout_postal_code, KeyboardType.Number)

        Section(stringResource(R.string.checkout_promo_code)) {
            if (state.appliedCode != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(state.appliedCode!!, style = MaterialTheme.typography.titleSmall)
                    TextLink(stringResource(R.string.checkout_remove_code), viewModel::removeCode)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.promoInput,
                        onValueChange = { v -> viewModel.edit { copy(promoInput = v, codeRejected = false) } },
                        placeholder = { Text(stringResource(R.string.checkout_promo_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = viewModel::applyCode, enabled = state.promoInput.isNotBlank() && !state.checkingCode) {
                        Text(stringResource(R.string.checkout_apply))
                    }
                }
                if (state.codeRejected) Text(stringResource(R.string.checkout_promo_invalid), color = AzzaTheme.colors.error)
            }
            Text(stringResource(R.string.checkout_one_code_only), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
        }

        Section(stringResource(R.string.checkout_summary)) {
            currentCart.lines.filter { it.available }.forEach { line ->
                AmountRow("${line.title} × ${line.quantity}", Money(line.unitPrice.discounted(line.discountPercentage).amountMinor * line.quantity, line.unitPrice.currency).format())
            }
            subtotal?.let { AmountRow(stringResource(R.string.order_subtotal), it.format()) }
            state.discount?.let { AmountRow(stringResource(R.string.order_discount), "−" + it.format()) }
            AmountRow(stringResource(R.string.order_shipping), stringResource(R.string.order_free))
            subtotal?.let {
                val total = Money((it.amountMinor - (state.discount?.amountMinor ?: 0)).coerceAtLeast(0), it.currency)
                AmountRow(stringResource(R.string.checkout_total), total.format(), strong = true)
            }
        }

        state.error?.let { Text(checkoutErrorText(it), color = AzzaTheme.colors.error) }

        Button(
            onClick = viewModel::placeOrder,
            enabled = state.canPlace && subtotal != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.placing) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
            else Text(stringResource(R.string.checkout_place_order))
        }
        TextLink(stringResource(R.string.checkout_back_to_cart), onBackToCart)
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    type: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    capitalize: Boolean = false,
) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(stringResource(label)) },
    singleLine = singleLine,
    minLines = if (singleLine) 1 else 2,
    keyboardOptions = KeyboardOptions(
        keyboardType = type,
        capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
    ),
    modifier = Modifier.fillMaxWidth(),
)

/** The web's checkoutErrorKey, same codes, same sentences. */
@Composable
private fun checkoutErrorText(error: AppError): String = when ((error as? AppError.Api)?.code) {
    "INSUFFICIENT_INVENTORY" -> stringResource(R.string.checkout_error_out_of_stock)
    "PRODUCT_NOT_AVAILABLE", "PRODUCT_NOT_FOUND", "BOOKING_REQUIRED" -> stringResource(R.string.checkout_error_unavailable)
    "SELF_PURCHASE_NOT_ALLOWED" -> stringResource(R.string.checkout_error_self_purchase)
    "DISCOUNT_CODE_INVALID", "DISCOUNT_CODE_LIMIT_REACHED" -> stringResource(R.string.checkout_error_discount)
    "CART_EMPTY" -> stringResource(R.string.basket_empty_heading)
    else -> if (error == AppError.Unauthenticated) stringResource(R.string.checkout_error_sign_in) else errorMessage(error)
}

@Composable
internal fun payErrorText(error: AppError): String =
    if ((error as? AppError.Api)?.code == "TEST_PAYMENTS_DISABLED") stringResource(R.string.order_test_payment_disabled)
    else stringResource(R.string.order_test_payment_error)
