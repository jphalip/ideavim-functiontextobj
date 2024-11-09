package com.julienphalip.ideavim.functiontextobj;

import static com.maddyhome.idea.vim.extension.VimExtensionFacade.putExtensionHandlerMapping;
import static com.maddyhome.idea.vim.extension.VimExtensionFacade.putKeyMappingIfMissing;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.maddyhome.idea.vim.api.ExecutionContext;
import com.maddyhome.idea.vim.api.VimEditor;
import com.maddyhome.idea.vim.api.VimInjectorKt;
import com.maddyhome.idea.vim.command.MappingMode;
import com.maddyhome.idea.vim.command.OperatorArguments;
import com.maddyhome.idea.vim.extension.ExtensionHandler;
import com.maddyhome.idea.vim.extension.VimExtension;
import com.maddyhome.idea.vim.state.mode.Mode;
import com.maddyhome.idea.vim.state.mode.SelectionType;
import java.awt.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FunctionTextObj implements VimExtension {

    @Override
    public @NotNull String getName() {
        return "functiontextobj";
    }

    @Override
    public void init() {
        // Register the extension handlers with <Plug> mappings
        putExtensionHandlerMapping(
                MappingMode.XO,
                VimInjectorKt.getInjector().getParser().parseKeys("<Plug>InnerFunction"),
                getOwner(),
                new FunctionHandler(false),
                false);
        putExtensionHandlerMapping(
                MappingMode.XO,
                VimInjectorKt.getInjector().getParser().parseKeys("<Plug>OuterFunction"),
                getOwner(),
                new FunctionHandler(true),
                false);

        // Map the default key bindings to the <Plug> mappings
        putKeyMappingIfMissing(
                MappingMode.XO,
                VimInjectorKt.getInjector().getParser().parseKeys("if"),
                getOwner(),
                VimInjectorKt.getInjector().getParser().parseKeys("<Plug>InnerFunction"),
                true);
        putKeyMappingIfMissing(
                MappingMode.XO,
                VimInjectorKt.getInjector().getParser().parseKeys("af"),
                getOwner(),
                VimInjectorKt.getInjector().getParser().parseKeys("<Plug>OuterFunction"),
                true);
    }

    private static Editor getEditor() {
        Window mostRecentFocusedWindow = WindowManager.getInstance().getMostRecentFocusedWindow();
        if (!(mostRecentFocusedWindow instanceof IdeFrame activeFrame)) return null;
        if (activeFrame.getProject() == null) return null;
        return FileEditorManager.getInstance(activeFrame.getProject()).getSelectedTextEditor();
    }

    private record FunctionHandler(boolean around) implements ExtensionHandler {

        @Override
        public void execute(
                @NotNull VimEditor vimEditor,
                @NotNull ExecutionContext context,
                @NotNull OperatorArguments operatorArguments) {
            Editor editor = getEditor();
            if (editor == null || editor.getProject() == null) return;
            VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
            if (file == null) return;
            PsiFile psiFile = PsiManager.getInstance(editor.getProject()).findFile(file);
            if (psiFile == null) return;

            // Find the function/method at or containing the current cursor position
            PsiElement method = findFunctionAtCursor(editor, psiFile);
            if (method == null) return;

            // Get the method boundaries
            int startOffset = method.getTextRange().getStartOffset();
            int endOffset = method.getTextRange().getEndOffset();

            if (!around) {
                PsiElement body = findFunctionBody(method);
                if (body != null) {
                    startOffset = body.getTextRange().getStartOffset();
                    endOffset = body.getTextRange().getEndOffset();
                    if (hasBraces(
                            body.getClass().getName(),
                            body.getNode().getElementType().toString())) {
                        startOffset += 1;
                        endOffset -= 1;
                    }
                }
            }

            // Set selection using editor's selection model
            SelectionModel selectionModel = editor.getSelectionModel();
            selectionModel.setSelection(startOffset, endOffset);
            editor.getCaretModel().moveToOffset(endOffset);

            // Update Vim mode to visual character-wise mode
            vimEditor.setMode(new Mode.VISUAL(SelectionType.CHARACTER_WISE, null));
        }

        private boolean hasBraces(String className, String elementType) {
            // JVM languages
            if (className.equals("com.intellij.psi.impl.source.tree.java.PsiCodeBlockImpl")
                    || (className.contains(".kotlin.") && elementType.equals("BLOCK"))
                    || (className.contains(".scala.") && elementType.contains("BLOCK"))) {
                return true;
            }

            // Web languages
            if ((className.contains(".javascript.") && elementType.equals("BLOCK_STATEMENT"))
                    || (className.contains(".php.") && elementType.equals("Group Statement"))) {
                return true;
            }

            // Systems programming
            if ((className.contains(".goide.") && elementType.equals("BLOCK"))
                    || (className.contains(".cpp.") && elementType.contains("COMPOUND_STATEMENT"))
                    || (className.contains(".rust.") && elementType.contains("BLOCK_EXPR"))) {
                return true;
            }

            // Mobile/Apps
            if ((className.contains(".swift.") && elementType.contains("BLOCK"))
                    || (className.contains(".dart.") && elementType.contains("BLOCK"))) {
                return true;
            }

            // Microsoft
            return className.contains(".dotnet.") && elementType.contains("BLOCK");
        }

        @Nullable
        private PsiElement findFunctionAtCursor(Editor editor, PsiFile psiFile) {
            int offset = editor.getCaretModel().getOffset();

            // Try to find the element at cursor
            PsiElement element = psiFile.findElementAt(offset);
            if (element == null) return null;

            // Walk up the PSI tree to find a function-like element
            element =
                    element.getParent(); // Start with parent since findElementAt usually returns a
            // leaf
            while (element != null) {
                String elementType = element.getNode().getElementType().toString().toUpperCase();

                // Specifically look for method/function declarations
                if (elementType.endsWith("FUNCTION_DECLARATION")
                        || elementType.equals("METHOD")
                        || elementType.equals("FUN")) {
                    return element;
                }

                // Continue up the tree
                element = element.getParent();
            }
            return null;
        }

        @Nullable
        private PsiElement findFunctionBody(PsiElement function) {
            // Try to find the function body among the children
            for (PsiElement child : function.getChildren()) {
                String elementType = child.getNode().getElementType().toString();
                if (elementType.contains("BLOCK")
                        || elementType.contains("BODY")
                        || elementType.equals("Py:STATEMENT_LIST")) {
                    return child;
                }
            }
            return null;
        }
    }
}
