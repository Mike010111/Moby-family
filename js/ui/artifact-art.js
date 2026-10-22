/**
 * Векторные заглушки артефактов для магазина. Показываются, пока в assets/artifacts
 * нет файлов artifact_nut.webp, artifact_coin.webp, artifact_gamepad.webp, artifact_diamond.webp.
 */
const ART = {
  nut: `<defs><radialGradient id="an-b" cx="40%" cy="45%" r="65%"><stop offset="0" stop-color="#C7834D"/><stop offset=".6" stop-color="#9A5A2C"/><stop offset="1" stop-color="#5E3216"/></radialGradient>
      <radialGradient id="an-c" cx="50%" cy="30%" r="70%"><stop offset="0" stop-color="#B98A5E"/><stop offset="1" stop-color="#6E4826"/></radialGradient></defs>
    <ellipse cx="50" cy="90" rx="26" ry="4" fill="#121B6E" opacity=".12"/>
    <path d="M50 30c19 0 30 16 30 31 0 16-13 27-30 27S20 77 20 61c0-15 11-31 30-31z" fill="url(#an-b)"/>
    <path d="M30 50c3 14 9 26 20 34" stroke="#F0B987" stroke-width="2.5" fill="none" stroke-linecap="round" opacity=".5"/>
    <path d="M22 44c3-14 15-24 28-24s25 10 28 24c-9-4-18-6-28-6s-19 2-28 6z" fill="url(#an-c)"/>
    <g stroke="#5A3A1C" stroke-width="1.2" opacity=".55"><path d="M32 33l3 8M41 28l2 9M50 26v10M59 28l-2 9M68 33l-3 8"/></g>
    <path d="M50 20c0-6 3-10 7-12" stroke="#5E3216" stroke-width="3" fill="none" stroke-linecap="round"/>`,
  coin: `<defs><linearGradient id="ac-o" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFE9A6"/><stop offset=".5" stop-color="#E5B53A"/><stop offset="1" stop-color="#A97813"/></linearGradient>
      <linearGradient id="ac-i" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset=".5" stop-color="#C9CED8"/><stop offset="1" stop-color="#8A91A0"/></linearGradient></defs>
    <ellipse cx="50" cy="90" rx="26" ry="4" fill="#121B6E" opacity=".12"/>
    <circle cx="50" cy="52" r="34" fill="url(#ac-o)"/>
    <circle cx="50" cy="52" r="24" fill="url(#ac-i)"/>
    <text x="50" y="64" text-anchor="middle" font-family="Inter, sans-serif" font-size="32" font-weight="800" fill="#6B7180">2</text>
    <path d="M27 33a30 30 0 0 1 22-13" stroke="#FFF7D6" stroke-width="3" fill="none" stroke-linecap="round"/>`,
  gamepad: `<defs><linearGradient id="ag-b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3A3D46"/><stop offset="1" stop-color="#121317"/></linearGradient></defs>
    <ellipse cx="50" cy="88" rx="32" ry="4" fill="#121B6E" opacity=".12"/>
    <path d="M28 34h44c10 0 16 8 18 20l3 16c1 8-6 13-12 9l-11-9H30L19 79c-6 4-13-1-12-9l3-16c2-12 8-20 18-20z" fill="url(#ag-b)"/>
    <path d="M32 46v14M25 53h14" stroke="#9AA3B8" stroke-width="4" stroke-linecap="round"/>
    <circle cx="66" cy="48" r="3.6" fill="#2334C8"/><circle cx="74" cy="55" r="3.6" fill="#E42C26"/><circle cx="58" cy="55" r="3.6" fill="#82AED3"/><circle cx="66" cy="62" r="3.6" fill="#E7F4FF"/>
    <path d="M30 38h40" stroke="#5B6070" stroke-width="2" stroke-linecap="round" opacity=".7"/>`,
  diamond: `<defs><linearGradient id="ad-a" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#BFE0FF"/></linearGradient>
      <linearGradient id="ad-b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#E7F4FF"/><stop offset="1" stop-color="#82AED3"/></linearGradient></defs>
    <ellipse cx="50" cy="90" rx="22" ry="4" fill="#121B6E" opacity=".12"/>
    <path d="M30 22h40l16 18-36 46-36-46z" fill="url(#ad-b)"/>
    <path d="M30 22l-16 18h72L70 22z" fill="url(#ad-a)"/>
    <g fill="none" stroke="#5C86C8" stroke-width="1.2" opacity=".7"><path d="M14 40h72M30 22l8 18 12-18 12 18 8-18M38 40l12 46 12-46"/></g>
    <path d="M36 28l-6 8" stroke="#fff" stroke-width="3" stroke-linecap="round"/>
    <path d="M78 14l2 5 5 2-5 2-2 5-2-5-5-2 5-2z" fill="#fff" stroke="#BFE0FF" stroke-width=".8"/>`,
};

export function artifactArt(id, label = '') {
  return `<svg class="artifact-art" viewBox="0 0 100 100" role="img" aria-label="${label}">${ART[id] ?? ART.coin}</svg>`;
}
