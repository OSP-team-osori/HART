/**
 * ============================================================================
 * [HART TADD] Dashboard 글로벌 통계 연동
 * ============================================================================
 */

document.addEventListener("DOMContentLoaded", () => {
    fetchGlobalStats();
});

async function fetchGlobalStats() {
    try {
        const response = await fetch('/api/v1/agent/global-stats');
        if (!response.ok) return;

        const data = await response.json();
        
        const costEl = document.getElementById('global-total-cost');
        const tokenEl = document.getElementById('global-total-tokens');
        
        if (costEl && data.totalCost != null) {
            costEl.textContent = '$' + data.totalCost.toFixed(2);
        }
        
        if (tokenEl && data.totalTokens != null) {
            tokenEl.textContent = Number(data.totalTokens).toLocaleString();
        }
    } catch (e) {
        console.warn("[Dashboard] 글로벌 통계 정보를 불러올 수 없습니다.", e);
    }
}
