/* =========================================================
   Gemini Chatbot - Logic giao dien
   ========================================================= */

// ---------- Trang thai ung dung ----------
const state = {
  mode: 'login',        // 'login' | 'register'
  username: '',
  sessions: [],
  currentSessionId: null,
  sending: false,
  pendingFile: null,   // file dang cho gui kem
};

// ---------- Phan tu DOM ----------
const $ = (id) => document.getElementById(id);

const authScreen  = $('authScreen');
const appScreen   = $('appScreen');
const authForm    = $('authForm');
const authError   = $('authError');
const authSubmit  = $('authSubmit');
const usernameEl  = $('username');
const passwordEl  = $('password');
const sessionList = $('sessionList');
const messagesEl  = $('messages');
const chatForm    = $('chatForm');
const inputEl     = $('input');
const sendBtn     = $('sendBtn');
const chatTitleEl = $('chatTitle');
const sidebar     = $('sidebar');
const scrim       = $('scrim');
const toastEl     = $('toast');
const confirmDlg  = $('confirmDialog');

// ---------- Bieu tuong SVG dung lai ----------
const ICON = {
  pencil: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z"/></svg>',
  trash:  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6M10 11v6M14 11v6"/></svg>',
  chat:   '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>',
};

// =========================================================
// Goi API
// =========================================================
async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    cache: 'no-store',
    ...options,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.error || 'Đã xảy ra lỗi, vui lòng thử lại');
    err.status = res.status;
    throw err;
  }
  return data;
}

// =========================================================
// Thong bao ngan
// =========================================================
let toastTimer = null;
function toast(message) {
  toastEl.textContent = message;
  toastEl.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toastEl.hidden = true; }, 2600);
}

// =========================================================
// MAN HINH DANG NHAP / DANG KY
// =========================================================
function setMode(next) {
  state.mode = next;
  const isLogin = next === 'login';

  $('tabLogin').classList.toggle('is-active', isLogin);
  $('tabRegister').classList.toggle('is-active', !isLogin);
  $('tabLogin').setAttribute('aria-selected', String(isLogin));
  $('tabRegister').setAttribute('aria-selected', String(!isLogin));

  authSubmit.querySelector('.btn-label').textContent = isLogin ? 'Đăng nhập' : 'Đăng ký';
  passwordEl.setAttribute('autocomplete', isLogin ? 'current-password' : 'new-password');

  clearAuthError();
}

function showAuthError(message) {
  authError.textContent = message;
  authError.hidden = false;
  authError.focus();                       // đưa tiêu điểm tới lỗi cho người dùng bàn phím
  usernameEl.setAttribute('aria-invalid', 'true');
  passwordEl.setAttribute('aria-invalid', 'true');
}

function clearAuthError() {
  authError.hidden = true;
  authError.textContent = '';
  usernameEl.removeAttribute('aria-invalid');
  passwordEl.removeAttribute('aria-invalid');
}

$('tabLogin').addEventListener('click', () => setMode('login'));
$('tabRegister').addEventListener('click', () => setMode('register'));

// Hien / an mat khau
$('togglePassword').addEventListener('click', (e) => {
  const btn = e.currentTarget;
  const showing = passwordEl.type === 'text';
  passwordEl.type = showing ? 'password' : 'text';
  btn.setAttribute('aria-pressed', String(!showing));
  btn.setAttribute('aria-label', showing ? 'Hiện mật khẩu' : 'Ẩn mật khẩu');
});

[usernameEl, passwordEl].forEach((el) => {
  el.addEventListener('input', () => {
    if (!authError.hidden) clearAuthError();
  });
});

authForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  clearAuthError();

  const username = usernameEl.value.trim();
  const password = passwordEl.value;

  // Kiem tra ngay tai cho truoc khi goi may chu
  if (username.length < 3) {
    showAuthError('Tên đăng nhập phải có ít nhất 3 ký tự');
    usernameEl.focus();
    return;
  }
  if (password.length < 6) {
    showAuthError('Mật khẩu phải có ít nhất 6 ký tự');
    passwordEl.focus();
    return;
  }

  authSubmit.disabled = true;
  authSubmit.classList.add('is-loading');

  try {
    const data = await api('/api/' + state.mode, {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
    await enterApp(data.username || username);
  } catch (err) {
    showAuthError(err.message);
  } finally {
    authSubmit.disabled = false;
    authSubmit.classList.remove('is-loading');
  }
});

// =========================================================
// CHUYEN MAN HINH
// =========================================================
async function enterApp(username) {
  state.username = username || '';
  authScreen.hidden = true;
  appScreen.hidden = false;

  $('userLabel').textContent = state.username ? '@' + state.username : 'Người dùng';
  $('avatar').textContent = (state.username || '?').charAt(0).toUpperCase();

  await loadSessions();
  inputEl.focus();
}

/**
 * Quay ve man hinh dang nhap.
 * Dat lai toan bo trang thai ngay tai cho thay vi location.reload(),
 * vi reload co the lay lai trang tu bo nho dem kem cookie cu.
 */
function backToAuth() {
  state.username = '';
  state.sessions = [];
  state.currentSessionId = null;
  state.sending = false;
  state.pendingFile = null;

  appScreen.hidden = true;
  authScreen.hidden = false;

  // Don sach du lieu cua phien truoc
  sessionList.innerHTML = '';
  messagesEl.innerHTML = '';
  chatTitleEl.textContent = 'Đoạn chat mới';
  authForm.reset();
  passwordEl.type = 'password';
  closeSidebar();
  clearAuthError();
  setMode('login');
  usernameEl.focus();
}

// =========================================================
// DANH SACH DOAN CHAT
// =========================================================
async function loadSessions() {
  try {
    const data = await api('/api/sessions');
    state.sessions = data.sessions || [];
    renderSessions();

    // Mo doan chat gan nhat, neu chua co thi de man hinh trong
    if (state.sessions.length > 0 && state.currentSessionId === null) {
      await openSession(state.sessions[0].id);
    } else if (state.sessions.length === 0) {
      showEmptyState();
    }
  } catch (err) {
    if (err.status === 401) { backToAuth(); return; }
    toast(err.message);
  }
}

function renderSessions() {
  sessionList.innerHTML = '';

  if (state.sessions.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'session-empty';
    empty.textContent = 'Chưa có đoạn chat nào';
    sessionList.appendChild(empty);
    return;
  }

  state.sessions.forEach((s) => {
    const item = document.createElement('div');
    item.className = 'session-item' + (s.id === state.currentSessionId ? ' is-active' : '');
    item.dataset.id = s.id;
    item.tabIndex = 0;
    item.setAttribute('role', 'button');
    item.setAttribute('aria-label', 'Mở đoạn chat: ' + s.title);

    const title = document.createElement('span');
    title.className = 'session-title';
    title.textContent = s.title;

    const actions = document.createElement('div');
    actions.className = 'session-actions';

    const renameBtn = document.createElement('button');
    renameBtn.className = 'session-action';
    renameBtn.type = 'button';
    renameBtn.innerHTML = ICON.pencil;
    renameBtn.setAttribute('aria-label', 'Đổi tên đoạn chat: ' + s.title);
    renameBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      startRename(item, s);
    });

    const deleteBtn = document.createElement('button');
    deleteBtn.className = 'session-action is-danger';
    deleteBtn.type = 'button';
    deleteBtn.innerHTML = ICON.trash;
    deleteBtn.setAttribute('aria-label', 'Xoá đoạn chat: ' + s.title);
    deleteBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      askDelete(s);
    });

    actions.append(renameBtn, deleteBtn);
    item.append(title, actions);

    item.addEventListener('click', () => openSession(s.id));
    item.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        openSession(s.id);
      }
    });

    sessionList.appendChild(item);
  });
}

// ---------- Doi ten tai cho ----------
function startRename(item, session) {
  const titleEl = item.querySelector('.session-title');
  if (!titleEl) return;

  const input = document.createElement('input');
  input.className = 'session-rename';
  input.value = session.title;
  input.setAttribute('aria-label', 'Tên mới cho đoạn chat');
  input.maxLength = 255;

  titleEl.replaceWith(input);
  input.focus();
  input.select();

  let finished = false;

  const commit = async () => {
    if (finished) return;
    finished = true;

    const newTitle = input.value.trim();

    // Bo trong hoac khong doi gi -> giu nguyen
    if (!newTitle || newTitle === session.title) {
      renderSessions();
      return;
    }

    try {
      await api('/api/sessions/' + session.id, {
        method: 'PATCH',
        body: JSON.stringify({ title: newTitle }),
      });
      session.title = newTitle;
      if (session.id === state.currentSessionId) {
        chatTitleEl.textContent = newTitle;
      }
      renderSessions();
      toast('Đã đổi tên đoạn chat');
    } catch (err) {
      if (err.status === 401) { backToAuth(); return; }
      toast(err.message);
      renderSessions();
    }
  };

  const cancel = () => {
    if (finished) return;
    finished = true;
    renderSessions();
  };

  input.addEventListener('blur', commit);
  input.addEventListener('keydown', (e) => {
    e.stopPropagation();
    if (e.key === 'Enter') { e.preventDefault(); commit(); }
    if (e.key === 'Escape') { e.preventDefault(); cancel(); }
  });
  input.addEventListener('click', (e) => e.stopPropagation());
}

// ---------- Xoa (co hop thoai xac nhan) ----------
let pendingDelete = null;
let lastFocused = null;

function askDelete(session) {
  pendingDelete = session;
  lastFocused = document.activeElement;
  $('confirmMsg').textContent =
    'Toàn bộ tin nhắn trong "' + session.title + '" sẽ bị xoá vĩnh viễn.';
  confirmDlg.hidden = false;
  $('confirmOk').focus();
}

function closeConfirm() {
  confirmDlg.hidden = true;
  pendingDelete = null;
  if (lastFocused && document.contains(lastFocused)) {
    lastFocused.focus();
  }
}

$('confirmCancel').addEventListener('click', closeConfirm);

$('confirmOk').addEventListener('click', async () => {
  const session = pendingDelete;
  if (!session) return;
  closeConfirm();

  try {
    await api('/api/sessions/' + session.id, { method: 'DELETE' });
    state.sessions = state.sessions.filter((s) => s.id !== session.id);

    // Dang mo doan chat vua xoa -> chuyen sang doan con lai
    if (state.currentSessionId === session.id) {
      state.currentSessionId = null;
      if (state.sessions.length > 0) {
        await openSession(state.sessions[0].id);
      } else {
        chatTitleEl.textContent = 'Đoạn chat mới';
        showEmptyState();
      }
    }
    renderSessions();
    toast('Đã xoá đoạn chat');
  } catch (err) {
    if (err.status === 401) { backToAuth(); return; }
    toast(err.message);
  }
});

// Dong hop thoai bang phim Esc hoac bam ra ngoai
confirmDlg.addEventListener('click', (e) => {
  if (e.target === confirmDlg) closeConfirm();
});

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && !confirmDlg.hidden) closeConfirm();
});

// ---------- Tao doan chat moi ----------
$('newChatBtn').addEventListener('click', () => {
  // Chua goi may chu voi - doan chat se duoc tao khi gui tin nhan dau tien
  state.currentSessionId = null;
  chatTitleEl.textContent = 'Đoạn chat mới';
  showEmptyState();
  renderSessions();
  closeSidebar();
  inputEl.focus();
});

// ---------- Mo mot doan chat ----------
async function openSession(sessionId) {
  state.currentSessionId = sessionId;

  const session = state.sessions.find((s) => s.id === sessionId);
  chatTitleEl.textContent = session ? session.title : 'Đoạn chat';

  renderSessions();
  closeSidebar();

  try {
    const data = await api('/api/history?sessionId=' + sessionId);
    messagesEl.innerHTML = '';

    if (!data.messages || data.messages.length === 0) {
      showEmptyState();
    } else {
      data.messages.forEach((m) => addMessage(m.role, m.content, m.attachment));
      scrollToBottom(false);
    }
  } catch (err) {
    if (err.status === 401) { backToAuth(); return; }
    toast(err.message);
  }
}

// =========================================================
// HIEN THI TIN NHAN
// =========================================================
function showEmptyState() {
  messagesEl.innerHTML = `
    <div class="empty" id="emptyState">
      <div class="empty-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 3l1.9 5.3L19 10l-5.1 1.7L12 17l-1.9-5.3L5 10l5.1-1.7L12 3z"/>
        </svg>
      </div>
      <h2>Bắt đầu cuộc trò chuyện</h2>
      <p>Đặt câu hỏi bất kỳ, Gemini sẽ trả lời và ghi nhớ ngữ cảnh.</p>
      <div class="suggestions">
        <button class="suggestion" type="button">Giải thích định luật Newton 1 ngắn gọn</button>
        <button class="suggestion" type="button">Viết hàm sắp xếp mảng bằng Java</button>
        <button class="suggestion" type="button">Gợi ý 5 chủ đề đồ án tốt nghiệp CNTT</button>
      </div>
    </div>`;
  bindSuggestions();
}

function bindSuggestions() {
  document.querySelectorAll('.suggestion').forEach((btn) => {
    btn.addEventListener('click', () => {
      inputEl.value = btn.textContent;
      autoResize();
      inputEl.focus();
      chatForm.requestSubmit();
    });
  });
}

function addMessage(role, content, attachment) {
  const isUser = role === 'user';
  const isError = role === 'error';

  const row = document.createElement('div');
  row.className = 'row ' + (isUser ? 'is-user' : isError ? 'is-model is-error' : 'is-model');

  const avatar = document.createElement('div');
  avatar.className = 'bubble-avatar';
  avatar.setAttribute('aria-hidden', 'true');
  avatar.textContent = isUser ? (state.username || '?').charAt(0).toUpperCase() : 'G';

  const bubble = document.createElement('div');
  bubble.className = 'bubble';

  // File dinh kem hien phia tren noi dung tin nhan
  if (attachment) {
    const wrap = document.createElement('div');
    wrap.className = 'bubble-attach';

    if ((attachment.type || '').startsWith('image/')) {
      const img = document.createElement('img');
      img.src = attachment.path;
      img.alt = attachment.name || 'Ảnh đính kèm';
      img.loading = 'lazy';
      img.addEventListener('click', () => openLightbox(img.src, img.alt));
      wrap.appendChild(img);
    } else {
      const card = document.createElement('a');
      card.className = 'attach-card';
      card.href = attachment.path;
      card.target = '_blank';
      card.rel = 'noopener noreferrer';
      card.innerHTML =
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" ' +
        'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>' +
        '<path d="M14 2v6h6"/></svg>';
      const label = document.createElement('span');
      label.textContent = attachment.name || 'Tệp đính kèm';
      card.appendChild(label);
      wrap.appendChild(card);
    }
    bubble.appendChild(wrap);
  }

  if (isUser || isError) {
    // Tin nguoi dung va thong bao loi: hien nguyen van, khong dien giai Markdown
    if (content) {
      const p = document.createElement('div');
      p.textContent = content;
      bubble.appendChild(p);
    }
  } else {
    // Cau tra loi cua Gemini la Markdown -> dung ra HTML.
    // renderMarkdown() da escape HTML truoc khi parse nen an toan.
    bubble.classList.add('md');
    const body = document.createElement('div');
    body.innerHTML = renderMarkdown(content);
    bubble.appendChild(body);
    bindCopyButtons(bubble);
  }

  row.append(avatar, bubble);
  messagesEl.appendChild(row);
  return row;
}

function addTyping() {
  const row = document.createElement('div');
  row.className = 'row is-model';
  row.innerHTML =
    '<div class="bubble-avatar" aria-hidden="true">G</div>' +
    '<div class="bubble"><div class="typing" aria-label="Gemini đang trả lời">' +
    '<span></span><span></span><span></span></div></div>';
  messagesEl.appendChild(row);
  scrollToBottom();
  return row;
}

function scrollToBottom(smooth = true) {
  messagesEl.scrollTo({
    top: messagesEl.scrollHeight,
    behavior: smooth ? 'smooth' : 'auto',
  });
}

// =========================================================
// GUI TIN NHAN
// =========================================================
chatForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  if (state.sending) return;

  const text = inputEl.value.trim();
  const file = state.pendingFile;

  // Phai co it nhat mot trong hai: chu hoac file
  if (!text && !file) return;

  const emptyState = $('emptyState');
  if (emptyState) emptyState.remove();

  // Hien ngay tin nhan cua nguoi dung, kem anh xem truoc neu co
  const localAttachment = file
    ? { name: file.name, type: file.type, path: URL.createObjectURL(file) }
    : null;
  addMessage('user', text, localAttachment);

  inputEl.value = '';
  autoResize();
  clearFile();
  scrollToBottom();

  state.sending = true;
  sendBtn.disabled = true;
  const typing = addTyping();

  try {
    let data;

    if (file) {
      // Co file -> gui dang multipart/form-data.
      // Khong dat Content-Type thu cong: trinh duyet tu dien kem boundary.
      const form = new FormData();
      form.append('message', text);
      if (state.currentSessionId) form.append('sessionId', state.currentSessionId);
      form.append('file', file);

      const res = await fetch('/api/chat', {
        method: 'POST',
        body: form,
        credentials: 'same-origin',
        cache: 'no-store',
      });
      data = await res.json().catch(() => ({}));
      if (!res.ok) {
        const err = new Error(data.error || 'Đã xảy ra lỗi, vui lòng thử lại');
        err.status = res.status;
        throw err;
      }
    } else {
      const payload = { message: text };
      if (state.currentSessionId) payload.sessionId = state.currentSessionId;
      data = await api('/api/chat', {
        method: 'POST',
        body: JSON.stringify(payload),
      });
    }

    typing.remove();
    addMessage('model', data.reply);

    // May chu vua tao doan chat moi -> cap nhat danh sach ben trai
    if (data.newSession) {
      state.currentSessionId = data.sessionId;
      state.sessions.unshift({
        id: data.sessionId,
        title: data.title || 'Đoạn chat mới',
        messageCount: 2,
      });
      chatTitleEl.textContent = data.title || 'Đoạn chat mới';
      renderSessions();
    } else {
      // Dua doan chat vua dung len dau danh sach
      const idx = state.sessions.findIndex((s) => s.id === state.currentSessionId);
      if (idx > 0) {
        const [moved] = state.sessions.splice(idx, 1);
        state.sessions.unshift(moved);
        renderSessions();
      }
    }
  } catch (err) {
    typing.remove();
    if (err.status === 401) {
      backToAuth();
      toast('Phiên đăng nhập đã hết hạn');
      return;
    }
    addMessage('error', err.message);
  } finally {
    state.sending = false;
    sendBtn.disabled = false;
    scrollToBottom();
    inputEl.focus();
  }
});

// =========================================================
// DINH KEM FILE
// =========================================================
const MAX_FILE_MB = 10;
const ALLOWED_EXT = [
  'png', 'jpg', 'jpeg', 'webp', 'gif', 'pdf',
  'txt', 'md', 'csv', 'json', 'xml', 'java', 'js', 'py', 'sql', 'html', 'css',
];

const fileInput   = $('fileInput');
const filePreview = $('filePreview');

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

/** Kiem tra va nhan mot file, tra ve true neu hop le. */
function setFile(file) {
  if (!file) return false;

  const ext = (file.name.split('.').pop() || '').toLowerCase();
  if (!ALLOWED_EXT.includes(ext)) {
    toast('Định dạng không hỗ trợ. Chỉ nhận: ảnh, PDF và file văn bản.');
    return false;
  }
  if (file.size > MAX_FILE_MB * 1024 * 1024) {
    toast('File quá lớn, tối đa ' + MAX_FILE_MB + 'MB');
    return false;
  }

  state.pendingFile = file;

  const img  = $('filePreviewImg');
  const icon = $('filePreviewIcon');

  if (file.type.startsWith('image/')) {
    img.src = URL.createObjectURL(file);
    img.hidden = false;
    icon.hidden = true;
  } else {
    img.hidden = true;
    icon.hidden = false;
  }

  $('filePreviewName').textContent = file.name;
  $('filePreviewSize').textContent = formatSize(file.size);
  filePreview.hidden = false;
  inputEl.focus();
  return true;
}

function clearFile() {
  state.pendingFile = null;
  fileInput.value = '';
  filePreview.hidden = true;
  const img = $('filePreviewImg');
  if (img.src.startsWith('blob:')) URL.revokeObjectURL(img.src);
  img.removeAttribute('src');
}

$('attachBtn').addEventListener('click', () => fileInput.click());
$('fileRemove').addEventListener('click', clearFile);
fileInput.addEventListener('change', () => setFile(fileInput.files[0]));

// ---------- Dan anh tu clipboard ----------
inputEl.addEventListener('paste', (e) => {
  const items = e.clipboardData?.items || [];
  for (const item of items) {
    if (item.kind === 'file') {
      const file = item.getAsFile();
      if (file && setFile(file)) {
        e.preventDefault();
        toast('Đã đính kèm ảnh từ clipboard');
      }
      return;
    }
  }
});

// ---------- Keo tha file vao cua so ----------
let dragDepth = 0;
let dropHint = null;

function showDropHint() {
  if (dropHint) return;
  dropHint = document.createElement('div');
  dropHint.className = 'drop-hint';
  dropHint.innerHTML =
    '<div class="drop-hint-box">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" ' +
      'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5-5 5 5M12 5v12"/></svg>' +
      '<p>Thả file vào đây để đính kèm</p>' +
      '<span>Ảnh, PDF hoặc file văn bản · tối đa ' + MAX_FILE_MB + 'MB</span>' +
    '</div>';
  document.body.appendChild(dropHint);
}

function hideDropHint() {
  dragDepth = 0;
  if (dropHint) {
    dropHint.remove();
    dropHint = null;
  }
}

window.addEventListener('dragenter', (e) => {
  if (appScreen.hidden) return;
  if (![...(e.dataTransfer?.types || [])].includes('Files')) return;
  e.preventDefault();
  dragDepth++;
  showDropHint();
});

window.addEventListener('dragover', (e) => {
  if (dropHint) e.preventDefault();
});

window.addEventListener('dragleave', () => {
  if (--dragDepth <= 0) hideDropHint();
});

window.addEventListener('drop', (e) => {
  if (appScreen.hidden) return;
  e.preventDefault();
  hideDropHint();
  const file = e.dataTransfer?.files?.[0];
  if (file) setFile(file);
});

// ---------- Xem anh phong to ----------
function openLightbox(src, alt) {
  const box = document.createElement('div');
  box.className = 'lightbox';
  box.innerHTML = '<img alt="">';
  box.querySelector('img').src = src;
  box.querySelector('img').alt = alt || '';
  box.addEventListener('click', () => box.remove());
  document.addEventListener('keydown', function esc(ev) {
    if (ev.key === 'Escape') {
      box.remove();
      document.removeEventListener('keydown', esc);
    }
  });
  document.body.appendChild(box);
}

// Enter de gui, Shift+Enter de xuong dong
inputEl.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    chatForm.requestSubmit();
  }
});

// O nhap tu gian theo noi dung
function autoResize() {
  inputEl.style.height = 'auto';
  inputEl.style.height = Math.min(inputEl.scrollHeight, 180) + 'px';
}
inputEl.addEventListener('input', autoResize);

// =========================================================
// SIDEBAR TREN MAN HINH NHO
// =========================================================
function openSidebar() {
  sidebar.classList.add('is-open');
  scrim.hidden = false;
  $('menuBtn').setAttribute('aria-expanded', 'true');
}

function closeSidebar() {
  sidebar.classList.remove('is-open');
  scrim.hidden = true;
  $('menuBtn').setAttribute('aria-expanded', 'false');
}

$('menuBtn').addEventListener('click', () => {
  if (sidebar.classList.contains('is-open')) closeSidebar();
  else openSidebar();
});

scrim.addEventListener('click', closeSidebar);

// =========================================================
// DANG XUAT
// =========================================================
$('logoutBtn').addEventListener('click', async () => {
  try {
    await api('/api/logout', { method: 'POST' });
  } catch {
    // Phien co the da het han san - van dua nguoi dung ve man hinh dang nhap
  }
  backToAuth();
  toast('Đã đăng xuất');
});

// =========================================================
// KHOI DONG
// =========================================================
(async function init() {
  bindSuggestions();
  try {
    const me = await api('/api/me');
    await enterApp(me.username || '');
  } catch {
    // Chua dang nhap - giu nguyen man hinh dang nhap
    usernameEl.focus();
  }
})();
