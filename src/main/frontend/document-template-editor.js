import { Editor } from 'https://esm.sh/@tiptap/core';
import { Extension } from 'https://esm.sh/@tiptap/core';
import BubbleMenu from 'https://esm.sh/@tiptap/extension-bubble-menu';
import StarterKit from 'https://esm.sh/@tiptap/starter-kit';
import { Plugin } from 'https://esm.sh/prosemirror-state';
import { Decoration, DecorationSet } from 'https://esm.sh/prosemirror-view';

const VARIABLE_PATTERN = /\{\{\{([a-zA-Zа-яА-Я0-9_]+)\}\}\}/g;
const VARIABLE_NAME_SANITIZE_PATTERN = /[^A-Za-zА-Яа-яЁё0-9_]/g;

const VariableHighlightExtension = Extension.create({
  name: 'variableHighlight',

  addProseMirrorPlugins() {
    return [
      new Plugin({
        props: {
          decorations(state) {
            const decorations = [];

            state.doc.descendants((node, position) => {
              if (!node.isText || !node.text) {
                return;
              }

              VARIABLE_PATTERN.lastIndex = 0;
              let match = VARIABLE_PATTERN.exec(node.text);
              while (match) {
                decorations.push(
                  Decoration.inline(
                    position + match.index,
                    position + match.index + match[0].length,
                    {
                      class: 'document-variable-tag',
                      'data-variable-name': match[1],
                    }
                  )
                );
                match = VARIABLE_PATTERN.exec(node.text);
              }
            });

            return DecorationSet.create(state.doc, decorations);
          },
        },
      }),
    ];
  },
});

function resolveEditorElement(hostElement, selector = '.element') {
  return hostElement?.querySelector(selector);
}

function sanitizeVariableName(value) {
  return (value ?? '').replace(VARIABLE_NAME_SANITIZE_PATTERN, '');
}

function destroyBubbleMenu(hostElement) {
  hostElement?.__documentTemplateBubbleMenu?.remove();
  delete hostElement.__documentTemplateBubbleMenu;
}

function createBubbleMenu(hostElement) {
  const menuElement = document.createElement('div');
  menuElement.className = 'document-editor-bubble-menu';

  const addVariableButton = document.createElement('button');
  addVariableButton.type = 'button';
  addVariableButton.className = 'document-editor-bubble-menu-button';
  addVariableButton.textContent = 'Добавить переменную';
  addVariableButton.addEventListener('mousedown', (event) => {
    event.preventDefault();
  });

  menuElement.append(addVariableButton);
  hostElement.append(menuElement);
  hostElement.__documentTemplateBubbleMenu = menuElement;

  return {
    menuElement,
    addVariableButton,
  };
}

function destroyVariableDialog(hostElement) {
  hostElement?.__documentTemplateVariableDialog?.overlay?.remove();
  delete hostElement.__documentTemplateVariableDialog;
}

function createVariableDialog(hostElement) {
  const overlay = document.createElement('div');
  overlay.className = 'document-variable-dialog-overlay';
  overlay.hidden = true;

  const dialogElement = document.createElement('div');
  dialogElement.className = 'document-variable-dialog';
  dialogElement.setAttribute('role', 'dialog');
  dialogElement.setAttribute('aria-modal', 'true');
  dialogElement.setAttribute('aria-label', 'Введите имя переменной');

  const title = document.createElement('div');
  title.className = 'document-variable-dialog-title';
  title.textContent = 'Введите имя переменной';

  const description = document.createElement('div');
  description.className = 'document-variable-dialog-description';
  description.textContent = 'Допустимы русские и латинские буквы, цифры и подчеркивание.';

  const input = document.createElement('input');
  input.type = 'text';
  input.className = 'document-variable-dialog-input';
  input.autocomplete = 'off';
  input.spellcheck = false;
  input.placeholder = 'variable_name';

  const validationMessage = document.createElement('div');
  validationMessage.className = 'document-variable-dialog-validation';

  const footer = document.createElement('div');
  footer.className = 'document-variable-dialog-footer';

  const cancelButton = document.createElement('button');
  cancelButton.type = 'button';
  cancelButton.className = 'document-variable-dialog-button document-variable-dialog-button-secondary';
  cancelButton.textContent = 'Отмена';

  const confirmButton = document.createElement('button');
  confirmButton.type = 'button';
  confirmButton.className = 'document-variable-dialog-button document-variable-dialog-button-primary';
  confirmButton.textContent = 'OK';

  footer.append(cancelButton, confirmButton);
  dialogElement.append(title, description, input, validationMessage, footer);
  overlay.append(dialogElement);
  hostElement.append(overlay);

  let submitHandler = null;

  const updateValidationState = () => {
    const sanitizedValue = sanitizeVariableName(input.value);
    if (input.value !== sanitizedValue) {
      input.value = sanitizedValue;
    }

    const isValid = sanitizedValue.length > 0;
    validationMessage.textContent = isValid ? '' : 'Введите имя переменной.';
    confirmButton.disabled = !isValid;
    return sanitizedValue;
  };

  const closeDialog = () => {
    overlay.hidden = true;
    submitHandler = null;
  };

  const submitDialog = () => {
    const sanitizedValue = updateValidationState();
    if (!sanitizedValue || !submitHandler) {
      return;
    }

    submitHandler(sanitizedValue);
    closeDialog();
  };

  input.addEventListener('input', updateValidationState);
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      submitDialog();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      closeDialog();
    }
  });
  cancelButton.addEventListener('click', closeDialog);
  confirmButton.addEventListener('click', submitDialog);
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) {
      closeDialog();
    }
  });

  const dialogApi = {
    overlay,
    open(onSubmit, initialValue = '') {
      submitHandler = onSubmit;
      input.value = sanitizeVariableName(initialValue);
      overlay.hidden = false;
      updateValidationState();
      window.requestAnimationFrame(() => {
        input.focus();
        input.select();
      });
    },
    close: closeDialog,
  };

  hostElement.__documentTemplateVariableDialog = dialogApi;
  return dialogApi;
}

window.initDocumentTemplateEditor = (hostElement, selector = '.element', htmlContent = '') => {
  const editorElement = resolveEditorElement(hostElement, selector);
  if (!editorElement) {
    return;
  }

  if (hostElement.__documentTemplateEditor) {
    hostElement.__documentTemplateEditor.destroy();
  }
  destroyBubbleMenu(hostElement);
  destroyVariableDialog(hostElement);

  const { menuElement, addVariableButton } = createBubbleMenu(hostElement);
  const variableDialog = createVariableDialog(hostElement);

  const editor = new Editor({
    element: editorElement,
    extensions: [
      StarterKit,
      VariableHighlightExtension,
      BubbleMenu.configure({
        pluginKey: 'documentTemplateBubbleMenu',
        element: menuElement,
        updateDelay: 0,
        options: {
          placement: 'top',
        },
        appendTo: () => hostElement,
        shouldShow: ({ editor: currentEditor }) => currentEditor.isEditable && currentEditor.view.hasFocus(),
      }),
    ],
    content: htmlContent || editorElement.innerHTML || '<p></p>',
  });

  addVariableButton.addEventListener('click', () => {
    const { from, to } = editor.state.selection;
    variableDialog.open((sanitizedVariableName) => {
      editor
        .chain()
        .focus()
        .insertContentAt({ from, to }, `{{{${sanitizedVariableName}}}}`)
        .run();
    });
  });

  hostElement.__documentTemplateEditor = editor;
};

window.saveDocumentTemplateEditor = (hostElement) => {
  const editor = hostElement?.__documentTemplateEditor;
  if (!editor) {
    return;
  }

  hostElement.$server.saveEditorContent(editor.getHTML());
};

window.destroyDocumentTemplateEditor = (hostElement) => {
  const editor = hostElement?.__documentTemplateEditor;
  if (!editor) {
    destroyBubbleMenu(hostElement);
    destroyVariableDialog(hostElement);
    return;
  }

  editor.destroy();
  delete hostElement.__documentTemplateEditor;
  destroyBubbleMenu(hostElement);
  destroyVariableDialog(hostElement);
};
