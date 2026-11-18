package com.mos.nira.features.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mos.nira.R
import com.mos.nira.core.ai.HybridInferenceResult

/** The text shown when an AI request fails: a clear message when the free limit is used up. */
@Composable
fun aiErrorText(result: HybridInferenceResult): String =
    if (result.limitReached) stringResource(R.string.ai_limit_reached)
    else stringResource(R.string.ai_read_error, result.errorMessage ?: "")
