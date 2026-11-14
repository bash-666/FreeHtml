package com.freehtml.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.rememberPdfViewerState
import com.freehtml.app.R

@OptIn(ExperimentalPdfApi::class)
@Composable
fun PdfPreview(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val loader = remember(context) { SandboxedPdfLoader(context) }
    val viewerState = rememberPdfViewerState()

    var pdfDocument by remember(uri) { mutableStateOf<PdfDocument?>(null) }
    var isLoading by remember(uri) { mutableStateOf(true) }
    var hasError by remember(uri) { mutableStateOf(false) }
    var reloadKey by remember(uri) { mutableIntStateOf(0) }

    LaunchedEffect(uri, reloadKey) {
        isLoading = true
        hasError = false
        pdfDocument = null
        runCatching { loader.openDocument(uri.toUri()) }
            .onSuccess { document ->
                pdfDocument = document
                isLoading = false
            }
            .onFailure {
                hasError = true
                isLoading = false
            }
    }

    DisposableEffect(pdfDocument) {
        val document = pdfDocument
        onDispose { document?.close() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val document = pdfDocument
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            document != null -> {
                PdfViewer(
                    pdfDocument = document,
                    state = viewerState,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            hasError -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.pdf_error_message),
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { reloadKey++ },
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.editor_retry),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
