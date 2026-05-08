package com.bahthia.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.nio.file.Path

/**
 * يُفعّل سحب الملفّات وإسقاطها على نافذة Compose Desktop.
 *
 * يَستهلك [onFilesDropped] قائمة المسارات المُسقَطة. يَعمل على مستوى النافذة كاملةً.
 * يَستعمل `Frame.getFrames()` للوصول لنافذة AWT الأساسيّة (الطريقة الأبسط في
 * Compose Desktop دون CompositionLocal صريح).
 */
@Composable
fun WindowFileDropTarget(onFilesDropped: (List<Path>) -> Unit) {
    DisposableEffect(Unit) {
        // نَأخذ أوّل نافذة AWT نشطة (Compose Desktop لها واحدة فقط في تطبيقنا)
        val frame = Frame.getFrames().firstOrNull { it.isShowing }
        if (frame == null) return@DisposableEffect onDispose { }

        val previous = frame.dropTarget
        val handler = object : DropTargetAdapter() {
            override fun drop(event: DropTargetDropEvent) {
                event.acceptDrop(DnDConstants.ACTION_COPY)
                try {
                    val transferable = event.transferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        onFilesDropped(files.map { it.toPath() })
                    }
                    event.dropComplete(true)
                } catch (_: Exception) {
                    event.dropComplete(false)
                }
            }
        }
        frame.dropTarget = DropTarget(frame, DnDConstants.ACTION_COPY, handler, true)

        onDispose {
            try { frame.dropTarget = previous } catch (_: Exception) { /* ignore */ }
        }
    }
}
