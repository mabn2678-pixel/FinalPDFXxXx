import sys

path = 'app/src/main/java/com/example/ui/ViewerScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

target = """                                            // Apply current UI states
                                            window.applyTheme('${state.readingTheme}');
                                            PDFViewerApplication.pdfViewer.scrollMode = ${if (state.snapToPage) 3 else if (state.scrollMode == "horizontal") 1 else 0};"""

replacement = """                                            // Apply current UI states
                                            window.applyTheme('${state.readingTheme}');
                                            PDFViewerApplication.pdfViewer.scrollMode = ${if (state.snapToPage) 3 else if (state.scrollMode == "horizontal") 1 else 0};
                                            if ('${state.activeEditTool}' !== 'none') {
                                                document.body.classList.add('edit-mode-active');
                                            } else {
                                                document.body.classList.remove('edit-mode-active');
                                            }"""

if target in content:
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content.replace(target, replacement))
    print('SUCCESS')
else:
    print('TARGET NOT FOUND')
