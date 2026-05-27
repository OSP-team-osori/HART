/**
 * ============================================================================
 * [HART TADD] 가상 터미널 실시간 스트리밍 제어 (SSE 연동)
 * ============================================================================
 * 백엔드 개발자님! 프론트엔드 통신 로직은 이 파일에 모두 모아두었습니다.
 * 실제 API 연동 시 아래 CONFIG 객체의 USE_MOCK을 false로 변경해주세요.
 */

const CONFIG = {
    // 🔴 백엔드 연동 시 이 값을 false로 변경해주세요!
    USE_MOCK: true,
    // 백엔드 SSE API 엔드포인트 주소
    API_URL: '/api/v1/agent/stream'
};

// 1. DOM 요소 선택
const terminalContainer = document.getElementById('terminal-content');

// 기존 하드코딩된 더미 텍스트를 비우고 시작
if (terminalContainer) {
    terminalContainer.innerHTML = '';
}

// 2. UI 렌더링 함수 (백엔드는 이 함수 내부 구현을 신경 쓸 필요 없습니다)
function renderLog(chunk) {
    if (!terminalContainer) return;

    // chunk에 개행문자(\n)가 있으면 div로 묶어주고, 아니면 span으로 이어붙임
    const lines = chunk.split('\n');

    lines.forEach((line, index) => {
        if (line === '') return; // 빈 줄 무시

        let colorClass = "text-gray-300"; // 기본 색상

        // 말머리 기반 색상 하이라이팅 처리
        if (line.includes("[INFO]")) {
            colorClass = "text-blue-400 font-semibold";
        } else if (line.includes("[WARN]")) {
            colorClass = "text-yellow-400 font-semibold";
        } else if (line.includes("[SUCCESS]")) {
            colorClass = "text-secondary font-bold";
        } else if (line.includes("[ERROR]") || line.includes("FAIL")) {
            colorClass = "text-danger font-bold";
        }

        const logElement = document.createElement('span');
        logElement.textContent = line;
        logElement.className = colorClass;

        terminalContainer.appendChild(logElement);

        // 줄바꿈 처리
        if (index < lines.length - 1 || chunk.endsWith('\n')) {
            terminalContainer.appendChild(document.createElement('br'));
        }
    });

    // 새 텍스트 추가 후 항상 맨 아래로 자동 스크롤
    terminalContainer.scrollTop = terminalContainer.scrollHeight;
}

// 3. 🔌 실제 백엔드 연동 로직 (SSE)
function connectRealBackend() {
    console.log("[Terminal] 백엔드 실시간 연결 시도 중...");
    
    // 백엔드 개발자님, 파싱 로직이 변경되면 아래 onmessage 내부를 수정해주세요.
    const eventSource = new EventSource(CONFIG.API_URL);

    eventSource.onmessage = (event) => {
        // 예상 응답 포맷: data: {"log": "[INFO] Agent started\n", "type": "info"}
        // (현재 코드는 mock의 'chunk' 구조를 기준으로 작성됨, 실제 규약에 맞춰 키 이름 수정 필요)
        const data = JSON.parse(event.data);
        renderLog(data.log || data.chunk); 
    };

    eventSource.onerror = () => {
        renderLog("\n[ERROR] 서버와의 연결이 끊어졌습니다.\n");
        eventSource.close();
    };
}

// 4. 🧪 UI 테스트용 가짜 로직 (프론트엔드 전용, mock/dummy_stream.json 기반)
async function runMockTerminal() {
    console.log("[Terminal] 더미 데이터 스트리밍 시작...");
    
    try {
        // 실제 JSON 파일을 불러옵니다 (프론트엔드 서버 환경 필요)
        const response = await fetch('../mock/dummy_stream.json');
        if (!response.ok) throw new Error("JSON 파일을 찾을 수 없습니다.");
        
        const dummyStreamData = await response.json();
        let currentIndex = 0;

        function processNextChunk() {
            if (currentIndex >= dummyStreamData.length) {
                // 스트리밍 종료 시 깜빡이는 커서 시뮬레이션
                renderLog("\n");
                const cursor = document.createElement('span');
                cursor.className = "blinking-cursor bg-white w-2 h-4 inline-block align-middle ml-2";
                terminalContainer.appendChild(cursor);
                terminalContainer.scrollTop = terminalContainer.scrollHeight;
                return;
            }

            const data = dummyStreamData[currentIndex];
            renderLog(data.chunk);
            currentIndex++;

            // delay 값(초)을 밀리초(ms)로 변환하여 다음 청크 예약
            setTimeout(processNextChunk, data.delay * 1000);
        }

        // 첫 청크 실행
        processNextChunk();
    } catch (error) {
        console.error("더미 데이터를 불러오는 중 에러 발생:", error);
        renderLog("[ERROR] 더미 데이터를 불러올 수 없습니다.\n", "error");
    }
}

// 5. 엔트리 포인트 (실행부)
document.addEventListener("DOMContentLoaded", () => {
    if (CONFIG.USE_MOCK) {
        runMockTerminal();
    } else {
        connectRealBackend();
    }
});
