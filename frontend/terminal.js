/**
 * ============================================================================
 * [HART TADD] 가상 터미널 실시간 스트리밍 제어 (SSE 연동)
 * ============================================================================
 * 프론트엔드 통신 로직은 이 파일에 모두 모아두었습니다.
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

let currentLineElement = null;

// 2. UI 렌더링 함수 (백엔드는 이 함수 내부 구현을 신경 쓸 필요 없습니다)
function renderLog(chunk) {
    if (!terminalContainer) return;

    // chunk를 개행문자로 분리 (예: ".\n" -> [".", ""])
    const parts = chunk.split('\n');

    parts.forEach((part, index) => {
        // 현재 줄이 없으면 새로 생성
        if (!currentLineElement) {
            currentLineElement = document.createElement('div');
            currentLineElement.className = "text-gray-300"; // 기본 색상
            currentLineElement.style.whiteSpace = "pre-wrap"; // 띄어쓰기 연속 유지
            terminalContainer.appendChild(currentLineElement);
        }

        // 텍스트 내용이 있다면 현재 줄에 이어붙이기
        if (part !== '') {
            currentLineElement.appendChild(document.createTextNode(part));

            // 내용이 추가될 때마다 전체 줄 텍스트를 기반으로 색상 하이라이팅 재평가
            const text = currentLineElement.textContent;
            if (text.includes("[INFO]")) {
                currentLineElement.className = "text-blue-400 font-semibold whitespace-pre-wrap";
            } else if (text.includes("[WARN]")) {
                currentLineElement.className = "text-yellow-400 font-semibold whitespace-pre-wrap";
            } else if (text.includes("[SUCCESS]")) {
                currentLineElement.className = "text-secondary font-bold whitespace-pre-wrap";
            } else if (text.includes("[ERROR]") || text.includes("FAIL")) {
                currentLineElement.className = "text-danger font-bold whitespace-pre-wrap";
            }
        }

        // 마지막 요소가 아니라면 뒤에 \n이 있었다는 뜻이므로 다음 텍스트는 새 줄로 시작
        if (index < parts.length - 1) {
            currentLineElement = null;
        }
    });

    // 매 chunk 처리 후 항상 줄을 끊어서, 다음 chunk는 새 줄에서 시작
    currentLineElement = null;

    // 새 텍스트 추가 후 항상 맨 아래로 자동 스크롤
    terminalContainer.scrollTop = terminalContainer.scrollHeight;
}

// 3. 실제 백엔드 연동 로직 (SSE)
function connectRealBackend() {
    console.log("[Terminal] 백엔드 실시간 연결 시도 중...");

    // 성욱님! 파싱 파직이 변경되면 아래 onmessage 내부를 수정해주세요.
    const eventSource = new EventSource(CONFIG.API_URL);

    eventSource.onmessage = (event) => {
        const data = JSON.parse(event.data);
        renderLog(data.log || data.chunk);
    };

    eventSource.onerror = () => {
        renderLog("\n[ERROR] 서버와의 연결이 끊어졌습니다.\n");
        eventSource.close();
    };
}

// 4. UI 테스트용 가짜 로직 (프론트엔드 전용, mock/dummy_stream.json 기반)
async function runMockTerminal() {
    console.log("[Terminal] 더미 데이터 스트리밍 시작...");

    try {
        // 실제 JSON 파일을 불러옵니다
        const response = await fetch('../mock/dummy_stream.json');
        if (!response.ok) throw new Error("JSON 파일을 찾을 수 없습니다.");

        const originalData = await response.json();

        // 스크롤 테스트를 위해 데이터를 메모리 상에서 5번 repeat (원본 JSON은 수정 안 함)
        const dummyStreamData = [];
        for (let i = 0; i < 5; i++) {
            dummyStreamData.push(...originalData);
        }

        let currentIndex = 0;

        function processNextChunk() {
            if (currentIndex >= dummyStreamData.length) {
                // 스트리밍 종료 시 깜빡이는 커서 시뮬레이션
                const cursor = document.createElement('span');
                cursor.className = "blinking-cursor bg-white w-2 h-4 inline-block align-middle ml-2";
                if (currentLineElement) {
                    currentLineElement.appendChild(cursor);
                } else {
                    const cursorWrap = document.createElement('div');
                    cursorWrap.appendChild(cursor);
                    terminalContainer.appendChild(cursorWrap);
                }
                terminalContainer.scrollTop = terminalContainer.scrollHeight;
                return;
            }

            const data = dummyStreamData[currentIndex];
            renderLog(data.chunk);
            currentIndex++;

            // delay 값(초)을 밀리초(ms)로 변환 (빠른 스크롤 테스트를 위해 딜레이를 약간 줄임)
            setTimeout(processNextChunk, (data.delay * 1000));
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
