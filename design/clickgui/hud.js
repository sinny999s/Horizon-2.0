// The HUD for the ClickGUI mockup: Horizon's 24 widgets (hud/Hud.kt, hud/widgets/) drawn in the new style at the
// owner's positions, sizes and settings (the dump's "hud"), and the HUD editor redesigned:
//   - a widgets panel (Horizon's checkbox list): every widget with a switch, a search, All / On / Off, the HUD config
//   - drag a widget to move it; it snaps to the edges and tops of the others with accent guides (Horizon: within 2
//     units, white guides; here within 3), and stays 5 units inside the screen
//   - right click a widget (or its gear in the panel) for its settings, as Horizon's popup of booleans
// The widgets keep Horizon's layout: the one-line plate (icon, divider, text), list headers with a plate per row, and
// the per-frame rules that stick a widget to its neighbours and to the right edge. Game values are a standing player
// at 426 98 -1287 on 6b6t.org; the potions, staff, players and cooldowns rows are samples.
(() => {
'use strict';

const CL = window.CL;
const { D, h, icon, sfx, toast, esc } = CL;
const $ = (s, r = document) => r.querySelector(s);
const stage = document.getElementById('stage');
const S = D.guiScale || 2;
const W = 1600, H = 900;
const u = n => n * S;

CL.ICONS.search = '<circle cx="11" cy="11" r="7"/><path d="M20.5 20.5L16 16"/>';

// Horizon lists them by id ("Fps Widget", "Coord Widget"); the panel shows plain names
const NAMES = { 'coord-widget': 'Coords', 'fps-widget': 'FPS', 'tps-widget': 'TPS', 'keybinds-widget': 'Key Binds', 'pvp-resources-widget': 'PvP Resources', 'array-list-widget': 'ArrayList' };
const plain = w => NAMES[w.id] || w.name.replace(/ Widget$/, '');
const widgets = D.hud.filter(w => typeof w === 'object').map(w => ({ ...w, set: Object.fromEntries(w.settings.map(s => [s.id, s])), w: 0, h: 0 }));
const byId = new Map(widgets.map(w => [w.id, w]));
const opt = (w, id) => w.set[id]?.value === true;
const state = { open: false, filter: 'all', query: '', drag: null };

// Horizon's widget icons (assets/horizon/vector/<id>.svg); the few without one get the GUI's own
const VECTOR = '../../src/main/resources/assets/horizon/vector/';
const HAS_SVG = new Set(['coord-widget', 'fps-widget', 'keybinds-widget', 'ping-widget', 'potions-widget', 'staff-widget', 'target-info', 'tps-widget',
	'watermark-widget', 'speed-widget', 'inventory-widget', 'crystal-speed-widget', 'chest-counter-widget', 'player-list-widget', 'cooldowns-widget', 'array-list-widget']);
// drawn for the widgets Horizon has no vector icon for, each after what the widget shows
Object.assign(CL.ICONS, {
	totem: '<rect x="8.5" y="3" width="7" height="5.5" rx="1.5"/><path d="M6 10h12l-1.6 8.3a2 2 0 0 1-2 1.7H9.6a2 2 0 0 1-2-1.7L6 10z"/><path d="M10 13.5h.01M14 13.5h.01M10.5 16.5h3"/>',
	bell: '<path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15l1.5-2z"/><path d="M10 21a2 2 0 0 0 4 0"/>',
	music: '<path d="M9 17.5V5.5l11-2v12"/><circle cx="6.5" cy="17.5" r="2.5"/><circle cx="17.5" cy="15.5" r="2.5"/>',
	wings: '<path d="M12 7C9.5 4.5 5.5 3.5 2.5 4.5c.5 4.5 3 9 9.5 13.5M12 7c2.5-2.5 6.5-3.5 9.5-2.5-.5 4.5-3 9-9.5 13.5M12 7v11"/><path d="M5.5 8l3.5 3M18.5 8L15 11"/>',
	phasewall: '<path d="M14 3v18M14 7h3M14 12h4M14 17h3"/><path d="M3 12h8M8 8.5l3.5 3.5L8 15.5"/>',
	cross: '<circle cx="12" cy="12" r="1.2"/><path d="M12 3v5M12 16v5M3 12h5M16 12h5"/>',
});
const FALLBACK = { 'pvp-resources-widget': 'totem', notifications: 'bell', 'media-player-widget': 'music', crosshair: 'cross',
	'flight-widget': 'wings', 'phase-widget': 'phasewall', 'macro-widget': 'key' };
function wicon(id) {
	if (id === 'armor-widget') return h('i', { class: 'wico', style: `--src:url(${VECTOR}armor.svg)` });
	if (HAS_SVG.has(id)) return h('i', { class: 'wico', style: `--src:url(${VECTOR}${id}.svg)` });
	return icon(FALLBACK[id] || 'misc', 'ico wico');
}

const DESC = {
	'coord-widget': 'Where you are, and in the Nether', 'fps-widget': 'Frames per second', 'keybinds-widget': 'Enabled modules that have a bind',
	'ping-widget': 'Your latency', 'potions-widget': 'Active effects and the time left', 'staff-widget': 'Staff detected on the server',
	'target-info': 'Your Aura or Auto Crystal target', 'tps-widget': 'Server ticks per second, and the average', 'watermark-widget': 'Your head, Horizon, your name and the server',
	notifications: 'Pop-ups when modules turn on or off', 'speed-widget': 'Your speed in km/h, mph or blocks/s', 'inventory-widget': 'Your 27 inventory slots',
	'crystal-speed-widget': 'Auto Crystal\'s crystals per second', 'media-player-widget': 'What\'s playing on your computer', 'armor-widget': 'Your armour and its durability',
	'chest-counter-widget': 'Single and double chests nearby', 'pvp-resources-widget': 'Totems, crystals, gapples, XP', 'player-list-widget': 'Players nearby, health and distance',
	crosshair: 'Spreads as you swing and move', 'flight-widget': 'Compass, pitch and stats while gliding', 'phase-widget': 'Your hitbox in the block you stand in',
	'cooldowns-widget': 'Items on cooldown, like pearls', 'macro-widget': 'Your item macros and their keys', 'array-list-widget': 'Every enabled module, with its mode',
};

// ---------- little item pictures ----------

const NETH = 'fill="#4b4452" stroke="#241f29" stroke-width="1.1"';
const SHEEN = 'fill="none" stroke="#7d6a9a" stroke-width=".9" opacity=".8"';
const ARMOR = [
	`<path d="M5 10a7 7 0 0 1 14 0v6h-3.5v-4h-7v4H5v-6z" ${NETH}/><path d="M7.5 9a4.5 4.5 0 0 1 9 0" ${SHEEN}/>`,
	`<path d="M7 4h3l2 2 2-2h3l3.5 4-3.5 2v10H7V10L3.5 8 7 4z" ${NETH}/><path d="M9.5 11h5M9.5 14h5" ${SHEEN}/>`,
	`<path d="M6 4h12v6l-2 10h-3.2L12 12l-.8 8H8L6 10V4z" ${NETH}/><path d="M6 7h12" ${SHEEN}/>`,
	`<path d="M3.5 12h5.5V7.5h3V17H3.5v-5zM12.5 7.5h3V12H21v5h-8.5V7.5z" ${NETH}/><path d="M4.5 15h3.5M16.5 15h3.5" ${SHEEN}/>`,
];
const ITEM = {
	totem: '<rect x="8" y="3" width="8" height="5" rx="1" fill="#ffd44d"/><path d="M6 9h12l-1.5 9h-9L6 9z" fill="#f2c230" stroke="#b58a12"/><circle cx="10" cy="12" r="1" fill="#2b6e3a"/><circle cx="14" cy="12" r="1" fill="#2b6e3a"/>',
	crystal: '<path d="M12 3l6 9-6 9-6-9 6-9z" fill="#ff8fd8" stroke="#b54aa0"/><path d="M12 7l3 5-3 5-3-5 3-5z" fill="#ffd6f3"/>',
	xp: '<path d="M9 3h6v4l3 3v10H6V10l3-3V3z" fill="#7be36a" fill-opacity=".85" stroke="#3a8a2c"/>',
	egap: '<circle cx="12" cy="13" r="7" fill="#ffd44d" stroke="#c99a1c"/><circle cx="12" cy="13" r="7" fill="#c86bff" opacity=".35"/><path d="M12 6c0-2 1.5-3 3-3" stroke="#6b4b2a" stroke-width="1.6"/>',
	gapple: '<circle cx="12" cy="13" r="7" fill="#ffd44d" stroke="#c99a1c"/><path d="M12 6c0-2 1.5-3 3-3" stroke="#6b4b2a" stroke-width="1.6"/>',
	firework: '<path d="M8 3h8l-1 4h-6L8 3z" fill="#c8c8c8"/><path d="M9 7h6v13H9V7z" fill="#d43c3c" stroke="#8e1f1f"/><path d="M9 12h6" stroke="#fff"/>',
	obby: '<rect x="5" y="5" width="14" height="14" rx="1.5" fill="#2b1f3d" stroke="#5c4580"/><path d="M8 9h3M13 14h3M9 15h1" stroke="#7d5fb0"/>',
	pearl: '<circle cx="12" cy="12" r="6.5" fill="#1d8f8a" stroke="#0e5552"/><circle cx="12" cy="12" r="3" fill="#7af0de"/>',
	sword: '<path d="M17 3h4v4L9.5 18.5l-4-4L17 3z" fill="#cfe3ff" stroke="#6d86a8"/><path d="M5 15l4 4M3.5 20.5l2-2" stroke="#7a5b3a" stroke-width="2.2"/>',
	pick: '<path d="M7 16L17 6M14 4l6 6" stroke="#6b4b2a" stroke-width="2.2"/><path d="M12 2.5l9.5 9.5-2 2L10 4.5l2-2z" fill="#4fd1e8" stroke="#2a8aa0"/>',
};
const svg = (inner, size) => `<svg viewBox="0 0 24 24" width="${size}" height="${size}" stroke-width="1.1">${inner}</svg>`;

// ---------- widget renderers ----------

const plate = (...kids) => h('div', { class: 'pl' }, ...kids);
const simple = (w, text) => h('div', { class: 'pl' }, wicon(w.id), h('i', { class: 'dv' }), h('span', null, text));
const isRight = w => w.x * W - W / 2 > 0;

// "Key Binds", "Potions"...: a header plate, then a row of plates per entry (right-aligned on the right half)
function list(w, title, rows) {
	const col = h('div', { class: 'hcol' + (isRight(w) ? ' right' : '') },
		h('div', { class: 'pl head' }, wicon(w.id), h('i', { class: 'dv' }), h('span', null, title)));
	for (const r of rows) col.append(h('div', { class: 'hrow2' }, r));
	return col;
}

const editorOn = () => state.open;
const POTIONS = [['Strength II', '2:41', false], ['Speed II', '4:12', false], ['Fire Resistance', '7:58', false], ['Weakness', '0:09', true]];
const STAFF = [['(near)', 'Helper_Kai'], ['Spec!', 'mod_vex']];
const PLAYERS = [['kitsune_ax', 20, 34, 42, true], ['xX_Obby_Xx', 14.5, 61, 88, false], ['lunarpvp', 7, 112, 130, false]];

const RENDER = {
	'coord-widget': w => {
		const other = opt(w, 'secondary') && !opt(w, 'only-nether');
		return simple(w, '426  98  -1287' + (other ? ' [53  -160]' : ''));
	},
	'fps-widget': w => simple(w, `${CL.stats.fps} fps`),
	'ping-widget': w => simple(w, `${CL.stats.ping} ms`),
	'tps-widget': w => simple(w, `${CL.stats.tps} (${CL.stats.tpsAvg}) t/s`),
	'speed-widget': w => simple(w, opt(w, 'bps') ? '0.0 b/s' : opt(w, 'mph') ? '0.0 mph' : '0.0 km/h'),
	'chest-counter-widget': w => simple(w, 'chests: S:3 D:1'),
	'crystal-speed-widget': w => simple(w, '0 c/s | 0 ping | 0 cpu | 0.0 ef.'),
	'keybinds-widget': w => {
		const rows = CL.modules.filter(m => m.id !== 'UI' && CL.isOn(m) && m.s[1].value.key !== -1)
			.map(m => [plate(m.name), plate(m.s[1].value.name)]);
		return rows.length || editorOn() ? list(w, 'Key Binds', rows) : null;
	},
	'potions-widget': w => list(w, 'Potions', POTIONS.map(([n, t, bad]) => [plate(h('span', { class: bad ? 'harm' : '' }, n)), plate(t)])),
	'staff-widget': w => list(w, 'Staff', STAFF.map(([s, n]) => [plate(h('span', { class: 'dimtext' }, s), n)])),
	'player-list-widget': w => list(w, 'Players', PLAYERS.map(([n, hp, d, p, fr]) => {
		const head = window.CLW.face(n);
		head.style.cssText = 'width:18px;height:18px;border-radius:4px;image-rendering:pixelated';
		return [plate(head, h('span', { class: fr ? 'friend' : '' }, n)), plate(`${hp} hp`), plate(`${d}m`), plate(`${p} ms`)];
	})),
	'cooldowns-widget': w => list(w, 'Cooldowns', [[plate('Ender Pearl'), plate('0:12'), plate(h('span', { html: svg(ITEM.pearl, 18) }))]]),
	'watermark-widget': () => {
		const head = window.CLW.face('Player');
		return h('div', { class: 'wm' }, head,
			plate(h('span', { class: 'hgrad' }, 'Horizon'), h('span', { class: 'dimtext' }, 'v0.1.0')),
			h('div', { class: 'hrow2' }, plate('Player'), plate('6b6t.org')));
	},
	'armor-widget': () => {
		const dura = [.94, .82, .88, .97];
		return h('div', { class: 'pl slots' }, ARMOR.map((a, i) => h('div', { class: 'slot2', html: svg(a, 30) }, h('div', { class: 'dura' }, h('i', { style: `width:${dura[i] * 100}%` })))));
	},
	'pvp-resources-widget': w => {
		const items = [['totems', 'totem', 7], ['crystals', 'crystal', 256], ['xp', 'xp', 64], ['gapples', 'egap', 12], ['crapples', 'gapple', 0], ['fireworks', 'firework', 64], ['obby', 'obby', 128]]
			.filter(([s]) => opt(w, s));
		return h('div', { class: 'pl slots res' }, items.map(([, it, n]) => h('div', { class: 'slot2', html: svg(ITEM[it], 30) }, h('b', null, n > 0 ? String(n) : '-'))));
	},
	'inventory-widget': w => {
		const fill = { 0: ['obby', 64], 1: ['obby', 64], 2: ['crystal', 64], 3: ['crystal', 64], 4: ['crystal', 64], 9: ['totem', 1], 10: ['totem', 1], 11: ['totem', 1], 12: ['egap', 12], 18: ['firework', 64], 19: ['xp', 64], 20: ['xp', 64], 26: ['pick', 1] };
		const grid = h('div', { class: 'inv' });
		for (let i = 0; i < 27; i++) {
			const f = fill[i];
			grid.append(h('i', { html: f ? svg(ITEM[f[0]], 22) + (f[1] > 1 ? `<b>${f[1]}</b>` : '') : '' }));
		}
		return h('div', { class: 'pl panel' }, h('div', { style: 'display:flex;align-items:center;gap:6px;height:26px' }, wicon(w.id), h('i', { class: 'dv' }), h('span', null, 'Inventory')), grid);
	},
	'target-info': () => {
		if (!editorOn()) return null;
		return h('div', { class: 'pl ti' }, window.CLW.face('Player'), h('b', null, 'Player'), h('div', { class: 'hp' }, h('i', { style: 'width:100%' })),
			h('small', null, '20.0 hp · 0 pops'),
			h('div', { class: 'tarmor', html: ARMOR.map(a => svg(a, 22)).join('') + svg(ITEM.sword, 22) + '<span>you, in the editor</span>' }));
	},
	'media-player-widget': () => editorOn()
		? h('div', { class: 'pl media' }, h('div', { class: 'art' }, icon('sound')), h('div', null, h('b', null, 'Nothing playing'), h('small', null, 'Media from your computer shows here')))
		: null,
	'flight-widget': () => editorOn()
		? h('div', { class: 'pl flight' }, h('div', { class: 'comp', html: '<i></i><b style="left:50%;top:4px;translate:-50% 0">Z</b><b style="right:6px;top:50%;translate:0 -50%">X</b><b style="left:50%;bottom:4px;translate:-50% 0">z</b><b style="left:6px;top:50%;translate:0 -50%">x</b>' }),
			h('div', { class: 'stats', html: 'Fireworks: 64<br>Durability: 412<br>Res (km): 18.4<br>Elytras: 3<br>XZ Speed: 0.0<br>Y Speed: 0.0' }))
		: null,
	'phase-widget': () => h('div', { class: 'pl phase' }, h('div', { class: 'blk' }, h('i'))),
	'macro-widget': () => h('div', { class: 'pl mtile', html: svg(ITEM.pearl, 26) + '<kbd>G</kbd>' }),
	notifications: () => null,
	crosshair: () => null,
};

// the array list keeps its rows, so they glide to new places and slide in and out
const alRows = new Map();
const measure = document.createElement('canvas').getContext('2d');
function arrayList(w, box) {
	measure.font = "500 15px SF, system-ui";
	const modes = opt(w, 'modes');
	const rows = CL.modules.filter(m => m.id !== 'UI' && CL.isOn(m)).map(m => {
		const md = modes && m.mode ? m.mode.value.toLowerCase().split('_').filter(Boolean).map(s => s[0].toUpperCase() + s.slice(1)).join(' ') : '';
		const rw = measure.measureText(m.name).width + 16 + (md ? measure.measureText(md).width + 18 : 0);
		return { m, md, rw };
	});
	rows.sort(opt(w, 'sort-by-width') ? (a, b) => b.rw - a.rw || a.m.name.localeCompare(b.m.name) : (a, b) => a.m.name.localeCompare(b.m.name));
	const right = isRight(w);
	box.classList.toggle('left', !right);
	const width = Math.max(0, ...rows.map(r => r.rw));
	for (const [id, el] of alRows) {
		if (rows.some(r => r.m.id === id)) continue;
		el.classList.add('out');
		alRows.delete(id);
		setTimeout(() => el.remove(), 400);
	}
	rows.forEach((r, i) => {
		let el = alRows.get(r.m.id);
		if (!el) {
			el = h('div', { class: 'r in' });
			box.append(el);
			alRows.set(r.m.id, el);
			requestAnimationFrame(() => requestAnimationFrame(() => el.classList.remove('in')));
		}
		el.replaceChildren(plate(r.m.name), ...(r.md ? [h('div', { class: 'pl dimtext' }, r.md)] : []));
		el.style.transform = `translate(${right ? width - r.rw : 0}px, ${i * u(14)}px)`;
	});
	box.style.width = width + 'px';
	box.style.height = Math.max(u(13), rows.length * u(14) - u(1)) + 'px';
	if (!rows.length && editorOn()) {
		box.style.width = '';
		if (!box.querySelector('.ph')) box.append(h('div', { class: 'pl ph' }, 'Array List'));
	} else box.querySelector('.ph')?.remove();
}

// ---------- layout ----------

const layer = h('div', { id: 'widgets' });
document.getElementById('hud').prepend(layer);
const xhair = h('div', { id: 'xhair' }, h('i'), h('i'), h('i'), h('i'));
const vxhair = h('div', { id: 'vxhair' });
document.getElementById('hud').append(xhair, vxhair);

function draw(w) {
	if (!w.el) {
		w.el = h('div', { class: 'hw', 'data-id': w.id });
		w.el.addEventListener('pointerdown', e => grab(w, e));
		w.el.addEventListener('contextmenu', e => { e.preventDefault(); if (state.open) settings(w, w.el); });
		layer.append(w.el);
	}
	if (w.id === 'array-list-widget') {
		let box = w.el.querySelector('.al2');
		if (!box) { box = h('div', { class: 'al2' }); w.el.replaceChildren(box); }
		arrayList(w, box);
	} else {
		const c = RENDER[w.id](w);
		w.el.replaceChildren(...(c ? [c] : []));
	}
	w.el.hidden = !w.enabled;
}

// Horizon's per-frame rules (HudWidget.frame), in pixels: stick to a neighbour it overlaps or sits next to, and hug
// the right edge when the right side is near it
function settle() {
	const live = widgets.filter(w => w.enabled && w.el && !w.el.hidden && w.w > 0);
	const clamp = (v, lo, hi) => lo > hi ? lo : Math.max(lo, Math.min(hi, v));
	for (let pass = 0; pass < 3; pass++) {
		for (const w of live) {
			if (state.drag?.w === w) continue;
			let left = w.x * W, top = w.y * H;
			for (const o of live) {
				if (o === w) continue;
				const ol = o.x * W, ot = o.y * H;
				const ylo = Math.min(u(5) / H, 1 - w.h / H), yhi = Math.max(u(5) / H, 1 - w.h / H);
				if (ol <= left && left <= ol + o.w && ot <= top && top <= ot + o.h) {
					if (ol !== left && ot === top) w.x = clamp((ol + o.w + u(2)) / W, u(5) / W, 1);
					if (ot !== top && (ol === left || ol + o.w === left + w.w)) w.y = clamp((ot + o.h + u(2)) / H, ylo, yhi);
				}
				left = w.x * W; top = w.y * H;
				if (ot === top && Math.abs(ol + o.w - left) < u(10)) w.x = clamp((ol + o.w + u(2)) / W, u(5) / W, 1);
				if ((ol === left || ol + o.w === left + w.w) && Math.abs(ot + o.h - top) < u(10)) w.y = clamp((ot + o.h + u(2)) / H, ylo, yhi);
				left = w.x * W; top = w.y * H;
			}
			const right = left + w.w;
			if (right > W - u(5) || Math.abs(right - W - u(5)) < u(60)) w.x = clamp((W - w.w - u(5)) / W, u(5) / W, 1);
		}
	}
}

function place() {
	for (const w of widgets) {
		if (!w.el || w.el.hidden) continue;
		w.w = w.el.offsetWidth;
		w.h = w.el.offsetHeight;
	}
	settle();
	for (const w of widgets) {
		if (!w.el) continue;
		w.el.style.translate = `${Math.round(w.x * W)}px ${Math.round(w.y * H)}px`;
	}
	const ch = byId.get('crosshair');
	xhair.hidden = !ch?.enabled;
	vxhair.hidden = !!ch?.enabled;
}

function update() {
	for (const w of widgets) draw(w);
	place();
}

// the numbers that change on their own (Horizon samples the frame rate every 4 ticks)
setInterval(() => {
	for (const id of ['fps-widget', 'ping-widget', 'tps-widget']) { const w = byId.get(id); if (w.enabled) draw(w); }
	place();
}, 500);

// ---------- the editor ----------

const frameEl = h('div', { id: 'hudframe' });
const guides = h('div');
stage.append(frameEl, guides);

function grab(w, e) {
	if (!state.open || e.button !== 0) return;
	e.preventDefault();
	try { w.el.setPointerCapture(e.pointerId); } catch { /* a pointer the browser no longer tracks */ }
	const [mx, my] = CL.toStage(e.clientX, e.clientY);
	state.drag = { w, gx: mx - w.x * W, gy: my - w.y * H };
	w.el.classList.add('drag');
	const others = widgets.filter(o => o !== w && o.enabled && !o.el.hidden && o.w > 0);
	const move = ev => {
		const [x, y] = CL.toStage(ev.clientX, ev.clientY);
		let left = Math.max(u(5), Math.min(W - w.w - u(5), x - state.drag.gx));
		let top = Math.max(Math.min(u(5), H - w.h), Math.min(Math.max(u(5), H - w.h), y - state.drag.gy));
		guides.replaceChildren();
		const near = 3 * S, drawn = new Set();
		const guide = (kind, at) => {
			if (drawn.has(kind + Math.round(at))) return;
			drawn.add(kind + Math.round(at));
			guides.append(h('i', { class: 'guide ' + kind, style: `${kind === 'v' ? 'left' : 'top'}:${Math.round(at)}px` }));
		};
		for (const o of others) {
			const ol = o.x * W, ot = o.y * H;
			if (Math.abs(ol + o.w - (left + w.w)) < near) { left = ol + o.w - w.w; guide('v', ol + o.w); }
			else if (Math.abs(ol - left) < near) { left = ol; guide('v', ol); }
			if (Math.abs(ot - top) < near) { top = ot; guide('h', ot); }
		}
		w.x = left / W;
		w.y = top / H;
		w.el.style.translate = `${Math.round(left)}px ${Math.round(top)}px`;
	};
	w.el.addEventListener('pointermove', move);
	w.el.addEventListener('pointerup', () => {
		w.el.removeEventListener('pointermove', move);
		w.el.classList.remove('drag');
		guides.replaceChildren();
		state.drag = null;
		update();
	}, { once: true });
	sfx.tick();
}

function settings(w, anchor) {
	CL.openPop(anchor, () => {
		const bools = w.settings.filter(s => s.type === 'bool');
		const head = h('div', { class: 'pophead' }, plain(w));
		if (!bools.length) return h('div', { style: 'width:220px' }, head, h('div', { class: 'opt', style: 'cursor:default' }, 'No settings'));
		return h('div', { style: 'width:220px' }, head, bools.map(s => {
			const sw = h('div', { class: 'sw' + (s.value ? ' on' : '') });
			return h('div', {
				class: 'opt', onclick: () => { s.value = !s.value; sw.classList.toggle('on', s.value); s.value ? sfx.on() : sfx.off(); update(); },
			}, s.name, h('span', { style: 'margin-left:auto' }, sw));
		}));
	}, { align: 'right' });
}

// the widgets panel
const panel = h('section', { id: 'hpanel' });
const count = h('span', { class: 'count' });
const cfg = h('button', { class: 'cfg' }, icon('layout'), D.hudConfig || 'default', icon('down'));
const head = h('header', { class: 'whead' }, h('span', { class: 'wi' }, icon('layout')), h('b', null, 'Widgets'), count, h('span', { class: 'sp' }), cfg);
const searchIn = h('input', { spellcheck: 'false', autocomplete: 'off', placeholder: 'Search widgets' });
const search = h('div', { class: 'fld hsearch' }, h('label', null, icon('search')), searchIn);
const segEl = h('div', { class: 'seg accent hseg' }, h('i', { class: 'thumb' }));
const listEl = h('div', { class: 'wl scroll' });
panel.append(head, search, segEl, listEl, h('div', { class: 'wfoot' },
	h('span', null, 'drag a widget to move it'), h('span', null, h('kbd', null, 'RMB'), 'settings'), h('span', null, h('kbd', null, 'Esc'), 'done')));
[['all', 'All'], ['on', 'On'], ['off', 'Off']].forEach(([v, label]) => {
	const b = h('button', { class: v === state.filter ? 'on' : '' }, label);
	b.onclick = () => { state.filter = v; segEl.querySelectorAll('button').forEach(x => x.classList.toggle('on', x === b)); CL.segThumb(segEl); sfx.tab(); drawList(); };
	segEl.append(b);
});
searchIn.oninput = () => { state.query = searchIn.value.trim().toLowerCase(); drawList(); };
searchIn.onkeydown = e => { if (e.key === 'Escape') { e.stopPropagation(); searchIn.value = ''; state.query = ''; drawList(); searchIn.blur(); } };
cfg.onclick = () => CL.openPop(cfg, () => h('div', { style: 'width:200px' },
	h('div', { class: 'pophead' }, 'HUD configs'),
	(D.hudConfigs || ['default']).map(c => h('div', { class: 'opt on', onclick: () => { CL.closePop(); toast('HUD', c + ' is already loaded', 'off', 'layout'); } }, icon('layout'), c, icon('check', 'ico ck'))),
	h('div', { class: 'popsep' }),
	h('div', { class: 'opt', onclick: () => { CL.closePop(); toast('HUD', 'saves the layout under a new name (Windows › Configs › HUD)', 'off', 'save'); } }, icon('save'), 'Save as...')), { align: 'right' });

function drawList() {
	listEl.replaceChildren();
	const shown = widgets.filter(w => (state.filter === 'all' || (state.filter === 'on') === w.enabled)
		&& (!state.query || plain(w).toLowerCase().includes(state.query) || (DESC[w.id] || '').toLowerCase().includes(state.query)));
	for (const w of shown) {
		const sw = h('div', { class: 'sw' + (w.enabled ? ' on' : '') });
		const gear = h('button', { class: 'gear2', title: 'Settings' }, icon('gear'));
		const row = h('div', { class: 'wli' + (w.enabled ? ' on' : '') }, h('div', { class: 'tico' }, wicon(w.id)),
			h('div', { class: 'tx' }, h('b', null, plain(w)), h('small', null, DESC[w.id] || '')), w.settings.some(s => s.type === 'bool') ? gear : null, sw);
		sw.onclick = e => {
			e.stopPropagation();
			w.enabled = !w.enabled;
			w.enabled ? sfx.on() : sfx.off();
			draw(w);
			if (w.enabled && w.el) { w.el.classList.remove('enter'); w.el.offsetWidth; w.el.classList.add('enter'); }
			update();
			drawList();
		};
		gear.onclick = e => { e.stopPropagation(); settings(w, gear); };
		row.onmouseenter = () => w.el?.classList.add('peek');
		row.onmouseleave = () => w.el?.classList.remove('peek');
		row.onclick = () => {
			if (!w.enabled || !w.el) return;
			w.el.classList.remove('flash');
			w.el.offsetWidth;
			w.el.classList.add('flash');
		};
		listEl.append(row);
	}
	if (!shown.length) listEl.append(h('div', { class: 'wempty' }, icon('search'), h('b', null, 'No widgets match')));
	count.textContent = `${widgets.filter(w => w.enabled).length}/${widgets.length} on`;
}

// the panel moves by its header, like a window
head.addEventListener('pointerdown', e => {
	if (e.button !== 0 || e.target.closest('button')) return;
	head.setPointerCapture(e.pointerId);
	panel.classList.add('moving');
	const [sx, sy] = CL.toStage(e.clientX, e.clientY), ox = panel.offsetLeft, oy = panel.offsetTop;
	const mv = ev => {
		const [x, y] = CL.toStage(ev.clientX, ev.clientY);
		panel.style.left = Math.max(-250, Math.min(1520, ox + x - sx)) + 'px';
		panel.style.top = Math.max(0, Math.min(850, oy + y - sy)) + 'px';
	};
	head.addEventListener('pointermove', mv);
	head.addEventListener('pointerup', () => { panel.classList.remove('moving'); head.removeEventListener('pointermove', mv); }, { once: true });
});

const nav = h('nav', { id: 'hnav', class: 'topnav', style: 'z-index:13' });
nav.innerHTML = '<svg class="logo"><use href="#mark"/></svg>';
const navTab = (ic, label, on, run) => { const b = h('button', { class: 'tab' + (on ? ' on' : '') }, icon(ic), label); b.onclick = run; nav.append(b); };
navTab('grid', 'Modules', false, () => { close(); CL.openGui(); });
navTab('macro', 'Windows', false, () => { close(true); window.CLW.open(); });
navTab('layout', 'HUD', true, () => {});
nav.append(h('i', { class: 'sep' }), h('span', { class: 'esc' }, h('kbd', null, 'Esc'), 'close'));
stage.append(panel, nav);

function open() {
	if (window.CLW?.isOpen()) window.CLW.close(false, true);
	CL.closeGui(true);
	state.open = true;
	document.body.classList.add('hudedit');
	sfx.open();
	update();
	drawList();
	requestAnimationFrame(() => CL.segThumb(segEl));
}

function close(quiet) {
	if (!state.open) return;
	state.open = false;
	CL.closePop();
	document.body.classList.remove('hudedit');
	widgets.forEach(w => w.el?.classList.remove('peek'));
	update();
	if (!quiet) sfx.close();
}

document.addEventListener('keydown', e => {
	if (!state.open || e.key !== 'Escape') return;
	e.preventDefault();
	if (document.activeElement === searchIn) return;
	close();
});

window.CLH = {
	open, close,
	isOpen: () => state.open,
	modulesChanged: () => { for (const id of ['array-list-widget', 'keybinds-widget']) draw(byId.get(id)); place(); },
	notifications: () => !!byId.get('notifications')?.enabled,
};
document.fonts.ready.then(update);
update();
})();
