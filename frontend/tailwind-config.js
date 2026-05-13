tailwind.config = {
    theme: {
        extend: {
            fontFamily: {
                sans: ['"Pretendard GOV"', 'Pretendard', '-apple-system', 'BlinkMacSystemFont', 'system-ui', 'Roboto', '"Helvetica Neue"', '"Segoe UI"', '"Apple SD Gothic Neo"', '"Noto Sans KR"', '"Malgun Gothic"', 'sans-serif'],
                mono: ['JetBrains Mono', 'monospace'],
            },
            colors: {
                dark: '#0B0C10',
                darker: '#050507',
                sidebar: 'rgba(18, 19, 24, 0.8)',
                box: '#A1A1AA',
                card: '#D4D4D8',
                panel: '#262626',
                fleetCard: '#181818',
                cardBorder: '#333333',
                primary: '#4F46E5', /* Indigo */
                secondary: '#22E990', /* Emerald */
                accent: '#F59E0B', /* Amber */
                danger: '#EF4444', /* Red */
            }
        }
    }
}
