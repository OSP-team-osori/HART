tailwind.config = {
    theme: {
        extend: {
            fontFamily: {
                sans: ['"Pretendard GOV"', 'Pretendard', '-apple-system', 'BlinkMacSystemFont', 'system-ui', 'Roboto', '"Helvetica Neue"', '"Segoe UI"', '"Apple SD Gothic Neo"', '"Noto Sans KR"', '"Malgun Gothic"', 'sans-serif']
            },
            colors: {
                dark: '#0A0A0A',
                darker: '#050505',
                sidebar: 'rgba(10, 12, 14, 0.85)',
                panel: '#161616',
                primary: '#00FF88',        /* Terminal Green — 유일한 포인트 컬러 */
                primaryMuted: '#00CC6A',   /* 차분한 초록 — 아이콘, 보조 텍스트 */
                primaryDim: '#003D20',     /* 어두운 초록 — 배경 tint, glow */
                danger: '#EF4444',         /* 에러/FAIL 전용 */
                warning: '#F59E0B',        /* 경고 전용 */
            }
        }
    }
}
