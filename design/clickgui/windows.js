// The Windows screen for the ClickGUI mockup: Horizon's five windows (gui/windows/: Configs, Waypoints, Proxies, Macros,
// Homies) redesigned to match the new GUI. Same contents and actions as the game's; what changed:
//   - every entry has its own controls (eye, radio, switch, bind) and a click opens it in the editor; right click still
//     does, as in Horizon, where left click toggled and right click edited
//   - windows come forward when clicked (Horizon's have no z-order), and Delete asks once more before deleting
//   - Homies removes a friend from its trash button, not from any click on the card
// The configs are the dev config's (run/horizon); its waypoint, proxy, macro and homie lists are empty, so those
// windows start with sample entries (documentation IPs, made-up names, heads drawn here instead of fetched).
(() => {
'use strict';

const CL = window.CL;
const { D, h, icon, sfx, toast, esc } = CL;
const $ = (s, r = document) => r.querySelector(s);

Object.assign(CL.ICONS, {
	eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
	eyeoff: '<path d="M3 3l18 18M10.6 5.6A9.6 9.6 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-3 3.7M6.6 6.6A17 17 0 0 0 2.5 12S6 18.5 12 18.5c1.8 0 3.4-.6 4.7-1.4M9.9 9.9a3 3 0 0 0 4.2 4.2"/>',
	trash: '<path d="M4 7h16M10 11v6M14 11v6M5.5 7l1 12.5A2 2 0 0 0 8.5 21h7a2 2 0 0 0 2-1.5L18.5 7M9 7V4.5A1.5 1.5 0 0 1 10.5 3h3A1.5 1.5 0 0 1 15 4.5V7"/>',
	chat: '<path d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H10l-4.5 4v-4H6.5A2.5 2.5 0 0 1 4 13.5v-8z"/>',
	sliders: '<path d="M4 7h10M18 7h2M4 17h4M12 17h8"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>',
	folder: '<path d="M3.5 7A2 2 0 0 1 5.5 5h4l2 2.5h7a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V7z"/>',
	load: '<path d="M12 4v11M7.5 10.5L12 15l4.5-4.5M5 19.5h14"/>',
	save: '<path d="M5 4h11l3 3v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4z"/><path d="M8 4v5h7V4M8 20v-6h8v6"/>',
	layout: '<rect x="3" y="4" width="18" height="16" rx="3"/><path d="M7 8h4M15 16h2M7 16h4"/>',
	signal: '<path d="M5 19v-3M10 19v-7M15 19V8M20 19V4"/>',
	grid: '<rect x="4" y="4" width="6.5" height="6.5" rx="2"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="2"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="2"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="2"/>',
});

// ---------- data ----------

const DIMS = ['Overworld', 'Nether', 'End'];
const waypoints = [
	{ id: 'spawn', server: '6b6t.org', x: 0, y: 64, z: 0, dim: 0, visible: true },
	{ id: 'nether-hub', server: '6b6t.org', x: 0, y: 120, z: 0, dim: 1, visible: true },
	{ id: 'death-1', server: '6b6t.org', x: -12480, y: 71, z: 3315, dim: 0, visible: true },
	{ id: '+x highway 50k', server: '6b6t.org', x: 50000, y: 120, z: 0, dim: 1, visible: false },
	{ id: 'end portal', server: '6b6t.org', x: 1888, y: 30, z: -1424, dim: 0, visible: false },
];
const proxies = [
	{ id: 'eu-fra', ip: '203.0.113.24', port: 1080, user: 'horizon', pass: 'hunter2', socks4: false, ping: 38 },
	{ id: 'us-east', ip: '198.51.100.7', port: 1080, user: '', pass: '', socks4: false, ping: 112 },
	{ id: 'backup', ip: '192.0.2.150', port: 9050, user: '', pass: '', socks4: true, ping: -1 },
];
let selectedProxy = proxies[0];
const macros = [
	{ type: 'chat', id: 'tpa homie', bind: 'J', enabled: true, message: '/tpa Steve' },
	{ type: 'item', id: 'pearl', bind: 'G', enabled: true, item: 'ender_pearl', legit: false },
	{ type: 'setting', id: 'timer', bind: 'LAlt', enabled: false, module: 'timer', setting: 'value', press: '2.0', release: '1.0', hold: true },
	{ type: 'chat', id: 'ez', bind: 'None', enabled: true, message: 'gg, Horizon on top' },
];
const homies = ['Steve', 'Alex', 'kitsune_ax', 'xX_Obby_Xx', 'lunarpvp'];
const configs = { modules: [...D.configs], hud: [...(D.hudConfigs || ['default'])] };
const current = { modules: D.config, hud: D.hudConfig || 'default' };
const ITEM_IDS = D.registries.items.map(([id]) => id.replace(/^(item|block)\.minecraft\./, ''));
const MODULE_IDS = CL.modules.map(m => m.id);
const TYPES = { chat: ['Chat', 'chat'], item: ['Item', 'box'], setting: ['Setting', 'sliders'] };

// ---------- shared pieces ----------

const state = { open: false, z: 10, listen: null, dialogs: new Set() };
const wins = new Map();
const root = h('div', { id: 'wins' });
document.getElementById('stage').append(root);

function seg(options, value, onPick) {
	const el = h('div', { class: 'seg accent' }, h('i', { class: 'thumb' }));
	const btns = options.map(([v, label]) => {
		const b = h('button', { class: v === value ? 'on' : '' }, label);
		b.onclick = e => {
			e.stopPropagation();
			if (b.classList.contains('on')) return;
			btns.forEach(x => x.classList.toggle('on', x === b));
			CL.segThumb(el);
			sfx.tab();
			onPick(v);
		};
		el.append(b);
		return b;
	});
	requestAnimationFrame(() => CL.segThumb(el));
	return el;
}

// "label | value", optionally masked (revealed by its eye) or with a completion from suggest() that Tab takes
function field(label, value, onInput, { suggest, mask, max, numeric, placeholder } = {}) {
	const inp = h('input', { spellcheck: 'false', autocomplete: 'off', placeholder: placeholder || '' });
	inp.value = value;
	if (max) inp.maxLength = max;
	const ghost = h('div', { class: 'ghost' });
	const el = h('div', { class: 'fld' }, h('label', null, label), ghost, inp);
	let sg = null;
	const update = () => {
		const v = inp.value;
		sg = suggest && v ? suggest().find(s => s.startsWith(v) && s !== v) : null;
		ghost.innerHTML = sg ? `<span>${esc(v)}</span><em>${esc(sg.slice(v.length))}</em>` : '';
		el.classList.toggle('has-sg', !!sg);
	};
	if (suggest) el.append(h('kbd', { class: 'tabhint' }, 'Tab'));
	if (mask) {
		inp.type = 'password';
		const r = h('button', { class: 'reveal', title: 'Show' }, icon('eye'));
		r.onclick = () => { inp.type = inp.type === 'password' ? 'text' : 'password'; r.replaceChildren(icon(inp.type === 'password' ? 'eye' : 'eyeoff')); };
		el.append(r);
	}
	inp.oninput = () => {
		if (numeric) inp.value = inp.value.replace(/[^\d-]/g, '');
		onInput(inp.value);
		update();
	};
	inp.onkeydown = e => {
		if (e.key === 'Tab' && sg) { e.preventDefault(); inp.value = sg; onInput(sg); update(); }
		if (e.key === 'Enter') inp.blur();
	};
	inp.onblur = () => { ghost.innerHTML = ''; el.classList.remove('has-sg'); };
	inp.onfocus = update;
	return el;
}

// Delete asks once more: the first click arms it for two seconds
function deleteButton(label, run) {
	const b = h('button', { class: 'danger' }, icon('trash'), label);
	let armed = null;
	b.onclick = () => {
		if (armed) { clearTimeout(armed); run(); return; }
		b.classList.add('confirm');
		b.lastChild.textContent = 'Click again to delete';
		sfx.tick();
		armed = setTimeout(() => { armed = null; b.classList.remove('confirm'); b.lastChild.textContent = label; }, 2000);
	};
	return b;
}

function empty(ic, title, text) {
	return h('div', { class: 'wempty' }, icon(ic), h('b', null, title), h('div', null, text));
}

function foot(...hints) {
	return h('div', { class: 'wfoot' }, hints.map(([k, t]) => h('span', null, k ? h('kbd', null, k) : null, t)));
}

// a dialog over a window's body (Horizon's Name / Create); Enter creates, Esc or Cancel closes
function dialog(w, { title, text, label = 'Name', max = 16, extra, create = 'Create', onCreate }) {
	let name = '';
	const ok = h('button', { class: 'wbtn primary', disabled: true }, create);
	const cancel = h('button', { class: 'wbtn' }, 'Cancel');
	const f = field(label, '', v => { name = v; ok.disabled = !v.trim(); }, { max });
	const card = h('div', { class: 'wdlg' }, h('h4', null, title), text ? h('p', null, text) : null, f, extra || null, h('div', { class: 'row2' }, cancel, ok));
	const dim = h('div', { class: 'wdim' }, card);
	const close = () => { state.dialogs.delete(close); dim.classList.add('bye'); setTimeout(() => dim.remove(), 180); };
	const submit = () => { if (!name.trim()) return; close(); onCreate(name.trim()); };
	ok.onclick = submit;
	cancel.onclick = close;
	dim.onpointerdown = e => { if (e.target === dim) close(); };
	$('input', f).addEventListener('keydown', e => { if (e.key === 'Enter') submit(); });
	w.el.append(dim);
	state.dialogs.add(close);
	setTimeout(() => $('input', f).focus(), 30);
	sfx.pop();
}

// a menu at the mouse, through the GUI's popover
function contextMenu(e, items) {
	const [x, y] = CL.toStage(e.clientX, e.clientY);
	const anchor = h('i', { style: `position:absolute;left:${x}px;top:${y - 6}px;width:1px;height:1px` });
	document.getElementById('stage').append(anchor);
	CL.openPop(anchor, () => h('div', { style: 'min-width:170px' }, items.map(([ic, label, run, danger]) => h('div', {
		class: 'opt', style: danger ? 'color:#ff8f8f' : '',
		onclick: () => { CL.closePop(); run(); },
	}, icon(ic), label))));
	setTimeout(() => anchor.remove(), 0);
}

// ---------- window frame ----------

function frame(def, i) {
	const count = h('span', { class: 'count' });
	const tools = h('div', { style: 'display:flex;gap:6px' });
	const head = h('header', { class: 'whead' }, h('span', { class: 'wi' }, icon(def.icon)), h('b', null, def.title), count, h('span', { class: 'sp' }), tools);
	const body = h('div', { class: 'wbody' });
	const grip = h('i', { class: 'wgrip' });
	const el = h('section', { class: 'win', style: `left:${def.x}px;top:${def.y}px;width:${def.w}px;height:${def.h}px` }, head, body, grip);
	const w = { def, el, body, count, tools, i };
	el.addEventListener('pointerdown', () => raise(w), true);
	head.addEventListener('pointerdown', e => {
		if (e.button !== 0 || e.target.closest('button')) return;
		head.setPointerCapture(e.pointerId);
		el.classList.add('moving');
		const [sx, sy] = CL.toStage(e.clientX, e.clientY), ox = el.offsetLeft, oy = el.offsetTop;
		const mv = ev => {
			const [x, y] = CL.toStage(ev.clientX, ev.clientY);
			el.style.left = Math.max(80 - el.offsetWidth, Math.min(1520, ox + x - sx)) + 'px';
			el.style.top = Math.max(0, Math.min(852, oy + y - sy)) + 'px';
		};
		head.addEventListener('pointermove', mv);
		head.addEventListener('pointerup', () => { el.classList.remove('moving'); head.removeEventListener('pointermove', mv); }, { once: true });
	});
	grip.addEventListener('pointerdown', e => {
		e.stopPropagation();
		grip.setPointerCapture(e.pointerId);
		el.classList.add('moving', 'resizing');
		const [sx, sy] = CL.toStage(e.clientX, e.clientY), ow = el.offsetWidth, oh = el.offsetHeight;
		const mv = ev => {
			const [x, y] = CL.toStage(ev.clientX, ev.clientY);
			el.style.width = Math.max(def.w, ow + x - sx) + 'px';
			el.style.height = Math.max(def.h, oh + y - sy) + 'px';
		};
		grip.addEventListener('pointermove', mv);
		grip.addEventListener('pointerup', () => { el.classList.remove('moving', 'resizing'); grip.removeEventListener('pointermove', mv); }, { once: true });
	});
	return w;
}

function raise(w) {
	if (w.el.classList.contains('front')) return;
	for (const o of wins.values()) o.el.classList.remove('front');
	w.el.style.zIndex = ++state.z;
	w.el.classList.add('front');
}

// a list on the left, the selected entry's editor on the right
function listEditor(w, { rows, renderRow, renderEditor, emptyTitle, emptyText, editorIcon, listWidth }) {
	const list = h('div', { class: 'wlist scroll', style: listWidth ? `width:${listWidth}px` : null });
	const editor = h('div', { class: 'weditor scroll' });
	w.body.append(list, editor);
	let sel = null;
	const api = {
		get sel() { return sel; },
		draw(enter) {
			list.innerHTML = '';
			rows().forEach((item, k) => {
				const r = renderRow(item);
				r.classList.add('wrow');
				r.classList.toggle('sel', item === sel);
				if (enter) { r.classList.add('enter'); r.style.animationDelay = k * 30 + 'ms'; }
				r.addEventListener('click', e => { if (!e.target.closest('button, .radio, .sw, .kbd')) api.select(item); });
				r.addEventListener('contextmenu', e => { e.preventDefault(); api.select(item); });
				r._item = item;
				list.append(r);
			});
			if (!rows().length) list.append(empty(w.def.icon, emptyTitle, emptyText));
			w.count.textContent = rows().length;
		},
		select(item, quiet) {
			sel = item;
			for (const r of list.children) r.classList?.toggle('sel', r._item === item);
			editor.innerHTML = '';
			if (!item) {
				editor.append(empty(editorIcon, 'Nothing open', 'Pick an entry on the left, or add one with +.'));
				return;
			}
			const parts = renderEditor(item);
			parts.forEach((p, k) => { if (p) { p.style.setProperty('--k', k); editor.append(p); } });
			editor.classList.remove('swap');
			editor.offsetWidth;
			editor.classList.add('swap');
			if (!quiet) sfx.tab();
		},
		refreshRow(item) {
			const old = [...list.children].find(r => r._item === item);
			if (!old) return;
			const r = renderRow(item);
			r.classList.add('wrow');
			r.classList.toggle('sel', item === sel);
			r._item = item;
			r.addEventListener('click', e => { if (!e.target.closest('button, .radio, .sw, .kbd')) api.select(item); });
			r.addEventListener('contextmenu', e => { e.preventDefault(); api.select(item); });
			old.replaceWith(r);
		},
		remove(item, arr) {
			const r = [...list.children].find(x => x._item === item);
			arr.splice(arr.indexOf(item), 1);
			if (r) r.classList.add('bye');
			setTimeout(() => { api.draw(); api.select(arr[0] || null, true); }, 300);
		},
	};
	return api;
}

// ---------- Configs ----------

function buildConfigs(w) {
	let tab = 'modules';
	const grid = h('div', { class: 'cgrid scroll' });
	const tabs = seg([['modules', 'Modules'], ['hud', 'HUD']], tab, v => { tab = v; draw(true); });
	w.body.style.flexDirection = 'column';
	w.body.append(h('div', { class: 'ctop' }, tabs), grid);
	w.el.append(foot(['2×', 'click loads'], ['RMB', 'more'], ['', 'drop a config file to import']));
	const save = h('button', { class: 'wbtn primary' }, icon('save'), 'Save as');
	save.onclick = () => saveAs();
	w.tools.append(save);
	const enabled = CL.modules.filter(CL.isOn).length;
	function saveAs() {
		dialog(w, {
			title: tab === 'modules' ? 'Save module config' : 'Save HUD config',
			text: 'Saves the current ' + (tab === 'modules' ? 'modules and settings' : 'HUD layout') + ' under a new name.',
			max: 10, create: 'Save',
			onCreate: name => {
				if (!configs[tab].includes(name)) configs[tab].push(name);
				toast('Configs', 'saved ' + name, 'on', 'save');
				draw(false, name);
			},
		});
	}
	function load(name, tile) {
		if (current[tab] === name) { toast('Configs', name + ' is already loaded', 'off', 'config'); return; }
		current[tab] = name;
		sfx.on();
		toast('Configs', 'loaded ' + name, 'on', 'load');
		draw(false, name);
	}
	function draw(enter, pop) {
		grid.innerHTML = '';
		configs[tab].forEach((name, k) => {
			const cur = current[tab] === name;
			const t = h('div', { class: 'ctile' + (cur ? ' cur' : '') + (enter ? ' enter' : '') + (pop === name ? ' loaded' : ''), style: `--k:${k}` },
				h('div', { class: 'ci' }, icon(tab === 'modules' ? 'grid' : 'layout')),
				cur ? h('span', { class: 'badge' }, 'Loaded') : null,
				h('div', null, h('b', null, name), h('small', null, cur ? (tab === 'modules' ? `${enabled} module${enabled === 1 ? '' : 's'} on` : 'current layout') : 'double click to load')));
			t.ondblclick = () => load(name, t);
			t.oncontextmenu = e => {
				e.preventDefault();
				e.stopPropagation();
				contextMenu(e, [
					['load', 'Load', () => load(name, t)],
					['folder', 'Open in Explorer', () => toast('Configs', `opens horizon/${tab === 'modules' ? 'modules' : 'widgets'}/ in Explorer`, 'off', 'folder')],
					['trash', 'Delete', () => {
						if (cur) { toast('Configs', "the loaded config can't be deleted", 'off', 'trash'); return; }
						configs[tab].splice(configs[tab].indexOf(name), 1);
						toast('Configs', 'deleted ' + name, 'off', 'trash');
						draw();
					}, true],
				]);
			};
			grid.append(t);
		});
		const add = h('div', { class: 'ctile add' + (enter ? ' enter' : ''), style: `--k:${configs[tab].length}` }, icon('plus'), h('span', null, 'Save current as...'));
		add.onclick = saveAs;
		grid.append(add);
		w.count.textContent = configs[tab].length;
	}
	grid.oncontextmenu = e => { e.preventDefault(); contextMenu(e, [['save', 'Save current as...', saveAs]]); };
	draw(true);
}

// ---------- Waypoints ----------

function buildWaypoints(w) {
	const add = h('button', { class: 'wbtn icon primary', title: 'Add one where you stand' }, icon('plus'));
	w.tools.append(add);
	w.el.append(foot(['', 'eye shows or hides it in the world'], ['RMB', 'edit']));
	const le = listEditor(w, {
		rows: () => waypoints,
		emptyTitle: 'No waypoints', emptyText: 'Add one with +; it goes where you stand.', editorIcon: 'pin',
		renderRow: p => {
			const eye = h('button', { class: 'eye' + (p.visible ? ' on' : ''), title: p.visible ? 'Hide' : 'Show' }, icon(p.visible ? 'eye' : 'eyeoff'));
			eye.onclick = () => { p.visible = !p.visible; p.visible ? sfx.on() : sfx.off(); le.refreshRow(p); };
			return h('div', { class: p.visible ? '' : 'dim' },
				h('div', { class: 'tx' }, h('b', null, p.id), h('small', null, `${p.server} · ${DIMS[p.dim]}`)), eye);
		},
		renderEditor: p => {
			const note = h('div', { class: 'fnote' });
			const conv = () => {
				const f = p.dim === 1 ? 8 : 1 / 8;
				note.innerHTML = p.dim === 2 ? 'The End has no Overworld or Nether position.'
					: `In the ${p.dim === 1 ? 'Overworld' : 'Nether'}: <b>${Math.round(p.x * f)}</b>, <b>${Math.round(p.z * f)}</b>`;
			};
			conv();
			const num = k => field(k.toUpperCase(), String(p[k]), v => { p[k] = parseInt(v, 10) || 0; conv(); }, { numeric: true });
			return [
				h('div', { class: 'frow' },
					field('Name', p.id, v => { p.id = v; le.refreshRow(p); }, { max: 16 }),
					field('Server', p.server, v => { p.server = v; le.refreshRow(p); })),
				h('div', { class: 'frow' }, num('x'), num('y'), num('z')),
				note,
				h('div', { class: 'flabel' }, 'Dimension'),
				seg(DIMS.map((d, k) => [k, d]), p.dim, v => { p.dim = v; conv(); le.refreshRow(p); }),
				h('div', { class: 'wacts' }, deleteButton('Delete waypoint', () => { toast('Waypoints', 'deleted ' + p.id, 'off', 'trash'); le.remove(p, waypoints); })),
			];
		},
	});
	add.onclick = () => dialog(w, {
		title: 'New waypoint', text: 'Placed where you stand: 426, 98, -1287 in the Overworld on 6b6t.org.',
		onCreate: id => {
			const p = { id, server: '6b6t.org', x: 426, y: 98, z: -1287, dim: 0, visible: true };
			waypoints.push(p);
			le.draw();
			le.select(p);
			toast('Waypoints', 'added ' + id, 'on', 'pin');
		},
	});
	le.draw(true);
	le.select(waypoints[0], true);
}

// ---------- Proxies ----------

function pingBadge(p) {
	const cls = p.ping === -2 ? 'wait' : p.ping < 0 ? '' : p.ping < 80 ? 'good' : p.ping < 150 ? 'ok' : 'bad';
	return h('span', { class: 'ping ' + cls }, h('i'), p.ping === -2 ? '...' : p.ping < 0 ? 'offline' : p.ping + ' ms');
}

function buildProxies(w) {
	const add = h('button', { class: 'wbtn icon primary', title: 'Add a proxy' }, icon('plus'));
	const pingAll = h('button', { class: 'wbtn icon', title: 'Ping them all' }, icon('signal'));
	w.tools.append(pingAll, add);
	w.el.append(foot(['', 'the selected proxy is used for the next connections'], ['RMB', 'edit']));
	const ping = (p, then) => {
		const base = p.ping;
		p.ping = -2;
		then();
		setTimeout(() => {
			p.ping = p.ip ? (p.id === 'backup' ? -1 : Math.max(12, Math.round((base > 0 ? base : 60) + (Math.random() - .5) * 24))) : -1;
			then();
		}, 500 + Math.random() * 700);
	};
	const le = listEditor(w, {
		rows: () => proxies,
		emptyTitle: 'No proxies', emptyText: 'Add one with +, then fill in its address.', editorIcon: 'globe',
		renderRow: p => {
			const radio = h('i', { class: 'radio' + (selectedProxy === p ? ' on' : ''), title: 'Use for the next connections' });
			radio.onclick = () => {
				selectedProxy = selectedProxy === p ? null : p;
				sfx.tick();
				toast('Proxies', selectedProxy ? 'connecting through ' + p.id : 'connecting directly', 'on', 'globe');
				proxies.forEach(x => le.refreshRow(x));
			};
			return h('div', null, radio,
				h('div', { class: 'tx' }, h('b', null, p.id), h('small', null, p.ip || 'no address yet')), pingBadge(p));
		},
		renderEditor: p => {
			const test = h('button', { class: 'wbtn' }, icon('signal'), 'Test');
			test.onclick = () => ping(p, () => le.refreshRow(p));
			const ip = field('IP', p.ip, v => { p.ip = v; le.refreshRow(p); }, { placeholder: '203.0.113.24' });
			ip.classList.add('wide');
			return [
				field('Name', p.id, v => { p.id = v; le.refreshRow(p); }, { max: 16 }),
				h('div', { class: 'frow' }, ip,
					field('Port', String(p.port || ''), v => { p.port = Math.abs(parseInt(v, 10) || 25565); le.refreshRow(p); }, { numeric: true, placeholder: '1080' })),
				field('Login', p.user, v => { p.user = v; }, { mask: true, placeholder: 'none' }),
				field('Pass', p.pass, v => { p.pass = v; }, { mask: true, placeholder: 'none' }),
				seg([[false, 'SOCKS5'], [true, 'SOCKS4']], p.socks4, v => { p.socks4 = v; le.refreshRow(p); }),
				h('div', { class: 'wacts' }, test, deleteButton('Delete', () => { toast('Proxies', 'deleted ' + p.id, 'off', 'trash'); le.remove(p, proxies); })),
			];
		},
	});
	pingAll.onclick = () => proxies.forEach(p => ping(p, () => le.refreshRow(p)));
	add.onclick = () => dialog(w, {
		title: 'New proxy', text: 'Give it a name, then its address in the editor.',
		onCreate: id => {
			const p = { id, ip: '', port: 0, user: '', pass: '', socks4: false, ping: -1 };
			proxies.push(p);
			le.draw();
			le.select(p);
		},
	});
	le.draw(true);
	le.select(proxies[0], true);
	w.pingAll = () => pingAll.onclick();
}

// ---------- Macros ----------

// the row's second line (its icon already says the type)
function macroSummary(m) {
	if (m.type === 'chat') return m.message || 'no text yet';
	if (m.type === 'item') return `${m.item || 'no item yet'} · ${m.legit ? 'Legit' : 'Rage'}`;
	return `${m.module || '?'}.${m.setting || '?'} ${m.press} → ${m.release}`;
}

function buildMacros(w) {
	const add = h('button', { class: 'wbtn icon primary', title: 'New macro' }, icon('plus'));
	w.tools.append(add);
	w.el.append(foot(['', 'switch turns it on'], ['', 'click the key to bind'], ['RMB', 'edit']));
	const le = listEditor(w, {
		rows: () => macros,
		emptyTitle: 'No macros', emptyText: 'A key that sends a chat line, uses an item or sets a setting.', editorIcon: 'macro', listWidth: 272,
		renderRow: m => {
			const key = h('span', { class: 'kbd' + (m.bind === 'None' ? ' none' : '') + (state.listen?.macro === m ? ' listen' : '') }, state.listen?.macro === m ? '...' : m.bind);
			key.onclick = () => { state.listen = { macro: m, redraw: () => le.refreshRow(m) }; sfx.tick(); le.refreshRow(m); };
			const sw = h('div', { class: 'sw' + (m.enabled ? ' on' : '') });
			sw.onclick = () => { m.enabled = !m.enabled; m.enabled ? sfx.on() : sfx.off(); le.refreshRow(m); };
			return h('div', { class: m.enabled ? '' : 'dim' }, h('div', { class: 'tico' + (m.enabled ? ' on' : '') }, icon(TYPES[m.type][1])),
				h('div', { class: 'tx' }, h('b', null, m.id), h('small', null, macroSummary(m))), key, sw);
		},
		renderEditor: m => {
			const re = () => le.refreshRow(m);
			const parts = [field('Name', m.id, v => { m.id = v; re(); }, { max: 10 })];
			if (m.type === 'chat') {
				parts.push(field('Text', m.message, v => { m.message = v; re(); }, { placeholder: '/command or message' }));
			} else if (m.type === 'item') {
				parts.push(field('Item', m.item, v => { m.item = v; re(); }, { suggest: () => ITEM_IDS }),
					h('div', { class: 'flabel' }, 'Use'),
					seg([[false, 'Rage'], [true, 'Legit']], m.legit, v => { m.legit = v; re(); }));
			} else {
				const settingIds = () => (CL.byId.get(m.module)?.settings || []).slice(2).filter(s => s.type !== 'group').map(s => s.id);
				parts.push(field('Module', m.module, v => { m.module = v; re(); }, { suggest: () => MODULE_IDS }),
					field('Setting', m.setting, v => { m.setting = v; re(); }, { suggest: settingIds }),
					h('div', { class: 'frow' }, field('Press', m.press, v => { m.press = v; re(); }), field('Release', m.release, v => { m.release = v; re(); })),
					seg([[false, 'Toggle'], [true, 'Hold']], m.hold, v => { m.hold = v; }));
			}
			parts.push(h('div', { class: 'wacts' }, deleteButton('Delete macro', () => { toast('Macros', 'deleted ' + m.id, 'off', 'trash'); le.remove(m, macros); })));
			return parts;
		},
	});
	add.onclick = () => {
		let type = 'chat';
		dialog(w, {
			title: 'New macro', max: 10,
			extra: seg(Object.entries(TYPES).map(([k, [label]]) => [k, label]), type, v => { type = v; }),
			onCreate: id => {
				const m = type === 'chat' ? { type, id, bind: 'None', enabled: true, message: '' }
					: type === 'item' ? { type, id, bind: 'None', enabled: true, item: '', legit: false }
					: { type, id, bind: 'None', enabled: true, module: '', setting: '', press: '0', release: '0', hold: false };
				macros.push(m);
				le.draw();
				le.select(m);
				toast('Macros', 'created ' + id, 'on', 'macro');
			},
		});
	};
	le.draw(true);
	le.select(macros[0], true);
}

// ---------- Homies ----------

// a Minecraft-style 8x8 face for a name, drawn here (Horizon fetches the player's head from minotar)
function face(name) {
	let s = [...name].reduce((a, c) => Math.imul(a ^ c.charCodeAt(0), 16777619), 2166136261) >>> 0;
	const rnd = () => ((s = Math.imul(s ^ (s >>> 15), 2246822507) >>> 0) / 4294967296);
	const pick = a => a[Math.floor(rnd() * a.length)];
	const skin = pick(['#f2c9a0', '#e3b088', '#c98d5e', '#8d5a3b', '#f6d7c3', '#b97a4f']);
	const hair = pick(['#2b1d14', '#4a2f1c', '#7a4a26', '#c8a26a', '#1b1b1f', '#d94b3b', '#e8d9a0', '#6b4bd8']);
	const eye = pick(['#3b6fd8', '#2f8f4c', '#5a3a22', '#2a2a2a', '#7a4fd8']);
	const c = document.createElement('canvas');
	c.width = c.height = 8;
	const g = c.getContext('2d');
	const px = (x, y, col) => { g.fillStyle = col; g.fillRect(x, y, 1, 1); };
	const shade = (col, k) => { const n = parseInt(col.slice(1), 16); const f = v => Math.max(0, Math.min(255, Math.round(v * k))); return `rgb(${f(n >> 16)},${f(n >> 8 & 255)},${f(n & 255)})`; };
	for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) px(x, y, shade(skin, .94 + rnd() * .12));
	for (let x = 0; x < 8; x++) { px(x, 0, shade(hair, .9 + rnd() * .2)); px(x, 1, shade(hair, .9 + rnd() * .2)); }
	px(0, 2, hair); px(7, 2, hair);
	if (rnd() > .4) { px(1, 2, hair); px(6, 2, hair); }
	if (rnd() > .5) { px(0, 3, hair); px(7, 3, hair); }
	px(1, 4, '#fff'); px(2, 4, eye); px(5, 4, eye); px(6, 4, '#fff');
	px(3, 5, shade(skin, .82)); px(4, 5, shade(skin, .82));
	const mouth = shade(skin, .62);
	px(2, 6, mouth); px(3, 6, mouth); px(4, 6, mouth); px(5, 6, mouth);
	if (rnd() > .7) for (let x = 1; x < 7; x++) px(x, 7, shade(hair, .9));
	return c;
}

function buildHomies(w) {
	w.body.style.flexDirection = 'column';
	const list = h('div', { class: 'hlist scroll' });
	const addField = field('', '', () => {}, { max: 16, placeholder: 'Add a homie by name' });
	addField.querySelector('label').append(icon('plus'));
	addField.classList.add('hadd');
	const inp = $('input', addField);
	inp.addEventListener('keydown', e => {
		if (e.key !== 'Enter' || !inp.value.trim()) return;
		const name = inp.value.trim();
		if (!homies.includes(name)) homies.push(name);
		inp.value = '';
		sfx.on();
		toast('Homies', name + ' is a homie now', 'on', 'users');
		draw(name);
	});
	const plus = h('button', { class: 'wbtn icon primary', title: 'Add a homie' }, icon('plus'));
	plus.onclick = () => inp.focus();
	w.tools.append(plus);
	w.body.append(addField, list);
	w.el.append(foot(['', 'not attacked, and shown as friends']));
	function draw(added) {
		list.innerHTML = '';
		homies.forEach(name => {
			const rm = h('button', { class: 'rm', title: 'Remove' }, icon('trash'));
			const row = h('div', { class: 'hrow' + (name === added ? ' enter' : '') }, face(name), h('b', null, name), rm);
			rm.onclick = () => {
				homies.splice(homies.indexOf(name), 1);
				row.classList.add('bye');
				sfx.off();
				toast('Homies', 'removed ' + name, 'off', 'users');
				setTimeout(() => draw(), 320);
			};
			list.append(row);
		});
		if (!homies.length) list.append(empty('users', 'No homies yet', 'Type a name above and press Enter.'));
		w.count.textContent = homies.length;
	}
	draw();
}

// ---------- the screen ----------

// Horizon's starting places (230x180 units at 50/290/530 x 50/240), in window pixels and the new sizes
const DEFS = [
	{ id: 'configs', title: 'Configs', icon: 'config', x: 100, y: 94, w: 440, h: 360, build: buildConfigs },
	{ id: 'waypoints', title: 'Waypoints', icon: 'pin', x: 560, y: 94, w: 470, h: 360, build: buildWaypoints },
	{ id: 'proxies', title: 'Proxies', icon: 'globe', x: 1050, y: 94, w: 450, h: 360, build: buildProxies },
	{ id: 'macros', title: 'Macros', icon: 'macro', x: 100, y: 472, w: 600, h: 360, build: buildMacros },
	{ id: 'homies', title: 'Homies', icon: 'users', x: 720, y: 472, w: 300, h: 360, build: buildHomies },
];

const mark = '<svg class="logo"><use href="#mark"/></svg>';
const nav = h('nav', { id: 'wnav', class: 'topnav' });
nav.innerHTML = mark;
const navTab = (ic, label, on, run) => { const b = h('button', { class: 'tab' + (on ? ' on' : '') }, icon(ic), label); b.onclick = run; nav.append(b); };
navTab('grid', 'Modules', false, () => close(true));
navTab('macro', 'Windows', true, () => {});
navTab('layout', 'HUD', false, () => { close(false, true); CL.hudEdit(); });
nav.append(h('i', { class: 'sep' }), h('span', { class: 'esc' }, h('kbd', null, 'Esc'), 'close'));
root.append(nav);

let built = false;
function build() {
	DEFS.forEach((def, i) => {
		const w = frame(def, i);
		wins.set(def.id, w);
		root.append(w.el);
		def.build(w);
	});
	built = true;
}

function open(focus) {
	if (!built) build();
	CL.closeGui(true);
	state.open = true;
	document.body.classList.add('wins');
	sfx.open();
	[...wins.values()].forEach((w, i) => setTimeout(() => { if (state.open) w.el.classList.add('shown'); }, 80 + i * 55));
	wins.get('proxies').pingAll();
	const f = focus && wins.get(focus);
	if (f) {
		raise(f);
		setTimeout(() => { f.el.classList.remove('pulse'); f.el.offsetWidth; f.el.classList.add('pulse'); }, 450);
	}
}

function close(toGui, quiet) {
	if (!state.open) return;
	state.open = false;
	state.listen = null;
	for (const c of [...state.dialogs]) c();
	CL.closePop();
	document.body.classList.remove('wins');
	for (const w of wins.values()) w.el.classList.remove('shown');
	if (toGui) CL.openGui();
	else if (!quiet) sfx.close();
}

// keys while the screen is open: a macro waiting for its bind takes the next key or button; Esc closes a dialog first
document.addEventListener('keydown', e => {
	if (!state.open) return;
	if (state.listen) {
		e.preventDefault();
		const l = state.listen;
		state.listen = null;
		l.macro.bind = ['Escape', 'Backspace', 'Delete'].includes(e.key) ? 'None' : CL.keyName(e);
		sfx.pop();
		l.redraw();
		return;
	}
	if (e.key === 'Escape') {
		e.preventDefault();
		if (state.dialogs.size) [...state.dialogs].pop()();
		else if (document.activeElement?.closest?.('#wins input')) document.activeElement.blur();
		else close();
	}
});
document.addEventListener('mousedown', e => {
	if (!state.open || !state.listen || e.target.closest?.('.wrow .kbd')) return;
	e.preventDefault();
	const l = state.listen;
	state.listen = null;
	l.macro.bind = CL.mouseName(e.button);
	sfx.pop();
	l.redraw();
}, true);

window.CLW = { open, close, isOpen: () => state.open, face };
})();
