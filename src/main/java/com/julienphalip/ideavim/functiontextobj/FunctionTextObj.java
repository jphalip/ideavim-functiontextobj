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
import com.maddyhome.idea.vim.api.ExecutionContext;
import com.maddyhome.idea.vim.api.VimEditor;
import com.maddyhome.idea.vim.api.VimInjectorKt;
import com.maddyhome.idea.vim.command.MappingMode;
import com.maddyhome.idea.vim.command.OperatorArguments;
import com.maddyhome.idea.vim.extension.ExtensionHandler;
import com.maddyhome.idea.vim.extension.VimExtension;
import com.maddyhome.idea.vim.newapi.IjVimEditorKt;
import com.maddyhome.idea.vim.state.mode.Mode;
import com.maddyhome.idea.vim.state.mode.SelectionType;
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

            // Get the method boundaries
            int startOffset = method.getTextRange().getStartOffset();
            int endOffset = method.getTextRange().getEndOffset();

            if (!around) {
                PsiElement body = findFunctionBody(method);
                if (body != null) {
                    startOffset = body.getTextRange().getStartOffset();
                    endOffset = body.getTextRange().getEndOffset();
                    if (usesBraces(body)) {
                        startOffset += 1;
                        endOffset -= 1;
                    }
                }
            }

            // Set the selection range for the text object
            SelectionModel selectionModel = editor.getSelectionModel();
            selectionModel.setSelection(startOffset, endOffset);

            if (vimEditor.getMode() instanceof Mode.OP_PENDING) {
                // Explicitly move the caret to the start of the selection
                editor.getCaretModel().moveToOffset(startOffset);
            } else {
                // For a visual command ('v'), we need to enter visual mode
                // and place the caret at the end of the selection
                editor.getCaretModel().moveToOffset(endOffset);
                vimEditor.setMode(new Mode.VISUAL(SelectionType.CHARACTER_WISE, new Mode.NORMAL()));
            }
        }

        private boolean usesBraces(PsiElement element) {
            return (element.getLanguage().getID().equalsIgnoreCase("C#")
                    || element.getLanguage().getID().equalsIgnoreCase("Scala")
                    || element.getLanguage().getID().equalsIgnoreCase("Dart")
                    || element.getLanguage().getID().equalsIgnoreCase("Go")
                    || element.getLanguage().getID().equalsIgnoreCase("Java")
                    || element.getLanguage().getID().equalsIgnoreCase("Kotlin")
                    || element.getLanguage().getID().equalsIgnoreCase("PHP")
                    || element.getLanguage().getID().equalsIgnoreCase("Perl5")
                    || element.getLanguage().getID().equalsIgnoreCase("R")
                    || element.getLanguage().getID().equalsIgnoreCase("Rust")
                    || element.getLanguage().getID().equalsIgnoreCase("TypeScript")
                    || element.getLanguage().getID().equalsIgnoreCase("JavaScript")
                    || element.getLanguage().getID().equalsIgnoreCase("ECMAScript 6"));
        }

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

                // Specifically look for method/function declarations
                if (elementType.endsWith("METHOD-DECLARATION") // C#
                        || elementType.startsWith("FUNCTION_DECLARATION") // Go, Dart
                        || elementType.endsWith("FUNCTION_DECLARATION") // Javascript, Python, Go
                        || elementType.endsWith("METHOD_DECLARATION") // Go
                        || elementType.equals("SUB_DEFINITION") // Perl
                        || elementType.equals("RUBY:METHOD") // Ruby
                        || elementType.equals("METHOD") // Java
                        || elementType.equals("FUN") // Kotlin
                        || elementType.equals("JS:TYPESCRIPT_FUNCTION") // Typescript
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

        @Nullable
        private PsiElement findFunctionBody(PsiElement function) {
            // Try to find the function body among the children
            for (PsiElement child : function.getChildren()) {
                String elementType = child.getNode().getElementType().toString().toUpperCase();
                if (elementType.equals("CODE_BLOCK") // Java
                        || elementType.equals("CS:BLOCK-LIST") // C#
                        || elementType.equals("FUNCTION_BODY") // Dart
                        || elementType.equals("BLOCK") // Kotlin, Rust
                        || elementType.equals("GROUP STATEMENT") // PHP
                        || elementType.equals("BLOCK OF EXPRESSIONS") // Scala
                        || elementType.equals("BLOCK_STATEMENT") // Typescript, Javascript
                        || elementType.equals("PERL5: BLOCK") // Perl
                        || elementType.equals("PY:STATEMENT_LIST") // Python
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
