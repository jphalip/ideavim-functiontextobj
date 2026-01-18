package com.julienphalip.ideavim.functiontextobj;

import static com.maddyhome.idea.vim.extension.VimExtensionFacade.putExtensionHandlerMapping;
import static com.maddyhome.idea.vim.extension.VimExtensionFacade.putKeyMappingIfMissing;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.maddyhome.idea.vim.KeyHandler;
import com.maddyhome.idea.vim.api.ExecutionContext;
import com.maddyhome.idea.vim.api.ImmutableVimCaret;
import com.maddyhome.idea.vim.api.VimEditor;
import com.maddyhome.idea.vim.api.VimInjectorKt;
import com.maddyhome.idea.vim.command.MappingMode;
import com.maddyhome.idea.vim.command.OperatorArguments;
import com.maddyhome.idea.vim.command.TextObjectVisualType;
import com.maddyhome.idea.vim.common.TextRange;
import com.maddyhome.idea.vim.extension.ExtensionHandler;
import com.maddyhome.idea.vim.extension.VimExtension;
import com.maddyhome.idea.vim.handler.TextObjectActionHandler;
import com.maddyhome.idea.vim.newapi.IjVimEditorKt;
import com.maddyhome.idea.vim.state.mode.Mode;
import com.maddyhome.idea.vim.state.mode.SelectionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IdeaVim extension that provides text objects for functions/methods. Supports 'if' (inner
 * function) and 'af' (around/outer function) text objects across multiple programming languages.
 */
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

    /**
     * TextObjectActionHandler that returns a fixed text range. This is the proper IdeaVim API for
     * implementing text objects in operator-pending mode.
     */
    static class EntireTextObjectHandler extends TextObjectActionHandler {
        final int start;
        final int end;

        EntireTextObjectHandler(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public @Nullable TextRange getRange(
                @NotNull VimEditor editor,
                @NotNull ImmutableVimCaret caret,
                @NotNull ExecutionContext context,
                int count,
                int rawCount) {
            return new TextRange(start, end);
        }

        @Override
        public @NotNull TextObjectVisualType getVisualType() {
            return TextObjectVisualType.CHARACTER_WISE;
        }
    }

    private record FunctionHandler(boolean around) implements ExtensionHandler {

        @Override
        public void execute(
                @NotNull VimEditor vimEditor,
                @NotNull ExecutionContext context,
                @NotNull OperatorArguments operatorArguments) {
            Editor editor = IjVimEditorKt.getIj(vimEditor);

            if (editor.getProject() == null) return;
            VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
            if (file == null) return;
            PsiFile psiFile = PsiManager.getInstance(editor.getProject()).findFile(file);
            if (psiFile == null) return;

            // Find the function/method at or containing the current cursor position
            PsiElement method = findFunctionAtCursor(editor, psiFile);
            if (method == null) return;

            // Get the method boundaries (entire method including signature)
            int startOffset = method.getTextRange().getStartOffset();
            int endOffset = method.getTextRange().getEndOffset();

            // For 'if' (inner function), adjust to only include the body content
            if (!around) {
                PsiElement body = findFunctionBody(method);
                if (body != null) {
                    startOffset = body.getTextRange().getStartOffset();
                    endOffset = body.getTextRange().getEndOffset();
                    // For block bodies, exclude the opening/closing braces
                    String bodyText = body.getText();
                    if (bodyText.startsWith("{") && bodyText.endsWith("}")) {
                        startOffset += 1;
                        endOffset -= 1;
                    }
                }
            }

            // Handle operator-pending mode (yank, delete, change, etc.)
            if (vimEditor.getMode() instanceof Mode.OP_PENDING) {
                // Use IdeaVim's proper text object API by adding a TextObjectActionHandler
                KeyHandler.getInstance()
                        .getKeyHandlerState()
                        .getCommandBuilder()
                        .addAction(new EntireTextObjectHandler(startOffset, endOffset));
            } else {
                // Handle visual mode (vif, vaf)
                SelectionModel selectionModel = editor.getSelectionModel();
                selectionModel.setSelection(startOffset, endOffset);
                editor.getCaretModel().moveToOffset(endOffset);
                vimEditor.setMode(new Mode.VISUAL(SelectionType.CHARACTER_WISE, new Mode.NORMAL()));
            }
        }

        /** Walk up the PSI tree from the cursor position to find the containing function/method. */
        @Nullable
        private PsiElement findFunctionAtCursor(Editor editor, PsiFile psiFile) {
            int offset = editor.getCaretModel().getOffset();

            // Try to find the element at cursor
            PsiElement element = psiFile.findElementAt(offset);
            if (element == null) return null;

            // Walk up the PSI tree to find a function-like element
            // Start with parent since findElementAt usually returns a leaf
            element = element.getParent();
            while (element != null) {
                if (element.getNode() == null) {
                    break;
                }

                String elementType = element.getNode().getElementType().toString().toUpperCase();
                // Check for method/function declaration element types across languages
                if (elementType.endsWith("METHOD-DECLARATION") // C#
                        || elementType.startsWith("FUNCTION_DEFINITION") // C, C++
                        || elementType.startsWith("FUNCTION_DECLARATION") // Go, Dart
                        || elementType.endsWith("FUNCTION_DECLARATION") // Javascript, Python, Go
                        || elementType.endsWith("METHOD_DECLARATION") // Go
                        || elementType.equals("SUB_DEFINITION") // Perl
                        || elementType.equals("RUBY:METHOD") // Ruby
                        || elementType.equals("METHOD") // Java
                        || elementType.equals("FUN") // Kotlin
                        || elementType.equals("JS:TYPESCRIPT_FUNCTION") // Typescript
                        || elementType.equals("JS:FUNCTION_EXPRESSION") // Javascript anonymous function / arrow function
                        || elementType.equals("JS:TYPESCRIPT_FUNCTION_EXPRESSION") // Typescript anonymous function / arrow function
                        || elementType.equals("JS:FUNCTION_PROPERTY") // Javascript object function property
                        || elementType.equals("JS:TYPESCRIPT_FUNCTION_PROPERTY") // Typescript object function property
                        || elementType.endsWith("FUNCTION DEFINITION") // Scala
                        || elementType.endsWith("FUNCTION") // PHP
                        || elementType.equals("CLASS_METHOD") // PHP
                        || elementType.endsWith("R_FUNCTION_EXPRESSION") // R
                ) {
                    return element;
                }

                // Continue up the tree
                element = element.getParent();
            }
            return null;
        }

        /** Find the body of a function (the part inside braces or indented block). */
        @Nullable
        private PsiElement findFunctionBody(PsiElement function) {
            // Try to find the function body among the children
            for (PsiElement child : function.getChildren()) {
                String elementType = child.getNode().getElementType().toString().toUpperCase();
                if (elementType.equals("CODE_BLOCK") // Java
                        || elementType.equals("LAZY_BLOCK") // C, C++
                        || elementType.equals("CS:BLOCK-LIST") // C#
                        || elementType.equals("FUNCTION_BODY") // Dart
                        || elementType.equals("BLOCK") // Rust, Kotlin block body
                        || elementType.equals("CALL_EXPRESSION") // Kotlin expression body
                        || elementType.equals("GROUP STATEMENT") // PHP
                        || elementType.equals("BLOCK OF EXPRESSIONS") // Scala
                        || elementType.equals("BLOCK_STATEMENT") // Typescript, Javascript
                        || elementType.equals("PERL5: BLOCK") // Perl
                        || elementType.equals("PYSTATEMENTLIST") // Python
                        || elementType.equals("BODY STATEMENT") // Ruby
                        || elementType.equals("R_BLOCK_EXPRESSION") // R
                ) {
                    return child;
                }
            }
            return null;
        }
    }
}
