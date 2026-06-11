document.addEventListener('DOMContentLoaded', () => {
    const sidebarContainer = document.getElementById('sidebar-container');
    if (!sidebarContainer) return;

    // Load saved width from localStorage
    const savedWidth = localStorage.getItem('sidebarWidth');
    if (savedWidth) {
        sidebarContainer.style.width = savedWidth + 'px';
    }

    // Fetch and inject sidebar HTML
    fetch('sidebar.html')
        .then(response => {
            if (!response.ok) throw new Error('Network response was not ok');
            return response.text();
        })
        .then(html => {
            sidebarContainer.innerHTML = html;
            initResizeHandle(sidebarContainer);
            highlightCurrentAgent();
            if (typeof initGithubTokenUI === 'function') initGithubTokenUI();
        })
        .catch(err => console.error('Failed to load sidebar:', err));
});

function initResizeHandle(sidebarContainer) {
    const handle = sidebarContainer.querySelector('.sidebar-resize-handle');
    if (!handle) return;

    let isResizing = false;

    handle.addEventListener('mousedown', (e) => {
        isResizing = true;
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none'; // Prevent text selection
    });

    document.addEventListener('mousemove', (e) => {
        if (!isResizing) return;
        
        let newWidth = e.clientX;
        if (newWidth < 280) newWidth = 280; // Minimum width
        if (newWidth > 500) newWidth = 500; // Maximum width
        
        sidebarContainer.style.width = newWidth + 'px';
    });

    document.addEventListener('mouseup', () => {
        if (isResizing) {
            isResizing = false;
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
            localStorage.setItem('sidebarWidth', sidebarContainer.style.width.replace('px', ''));
        }
    });
}

function highlightCurrentAgent() {
    // URL 기반으로 활성화된 에이전트를 하이라이트할 수 있음 (선택 사항)
    // 현재는 HTML에 스타일이 하드코딩되어 있으므로 보류
}
