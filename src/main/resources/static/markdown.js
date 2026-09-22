/* =========================================================
   Bộ chuyển Markdown -> HTML, viết gọn cho nhu cầu của chatbot.
   Không dùng thư viện ngoài để giữ dự án tối thiểu dependency.

   AN TOÀN: escape toàn bộ HTML TRƯỚC khi parse. Nội dung do Gemini
   trả về được coi là không đáng tin — nếu nó trả về <script> thì
   phải hiện ra dưới dạng chữ, không được chạy.
   ========================================================= */

/** Biến ký tự đặc biệt của HTML thành thực thể an toàn. */
function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** Xử lý định dạng trong một dòng: đậm, nghiêng, gạch ngang, code, liên kết. */
function inline(text) {
  return text
    // `code` — làm trước để nội dung bên trong không bị parse tiếp
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    // **đậm** và __đậm__
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/__([^_]+)__/g, '<strong>$1</strong>')
    // *nghiêng* và _nghiêng_
    .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
    .replace(/(^|[^_])_([^_\n]+)_/g, '$1<em>$2</em>')
    // ~~gạch ngang~~
    .replace(/~~([^~]+)~~/g, '<del>$1</del>')
    // [chữ](liên kết) — chỉ nhận http/https, chặn javascript:
    .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
             '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
}

/**
 * Chuyển Markdown thành HTML.
 * Hỗ trợ: tiêu đề, khối code, danh sách, trích dẫn, bảng, đường kẻ ngang.
 */
function renderMarkdown(source) {
  if (!source) return '';

  // Escape NGAY TỪ ĐẦU - mọi thứ sau đây đều làm trên văn bản đã an toàn
  const text = escapeHtml(source).replace(/\r\n/g, '\n');
  const lines = text.split('\n');

  const out = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // ----- Khối code: ```ngôn-ngữ -----
    const fence = line.match(/^\s*```(\w*)/);
    if (fence) {
      const lang = fence[1] || '';
      const body = [];
      i++;
      while (i < lines.length && !/^\s*```/.test(lines[i])) {
        body.push(lines[i]);
        i++;
      }
      i++;   // bỏ qua dòng ``` đóng

      const code = body.join('\n');
      out.push(
        '<div class="code-block">' +
          '<div class="code-head">' +
            '<span class="code-lang">' + (lang || 'code') + '</span>' +
            '<button class="code-copy" type="button" data-code="' +
              encodeURIComponent(code) + '">Sao chép</button>' +
          '</div>' +
          '<pre><code>' + code + '</code></pre>' +
        '</div>'
      );
      continue;
    }

    // ----- Đường kẻ ngang: --- hoặc *** -----
    if (/^\s*([-*_])\s*\1\s*\1[\s\-*_]*$/.test(line)) {
      out.push('<hr>');
      i++;
      continue;
    }

    // ----- Tiêu đề: # đến ###### -----
    const heading = line.match(/^(#{1,6})\s+(.*)$/);
    if (heading) {
      const level = Math.min(heading[1].length + 1, 6);   // # -> h2 (h1 dành cho trang)
      out.push('<h' + level + '>' + inline(heading[2]) + '</h' + level + '>');
      i++;
      continue;
    }

    // ----- Bảng: | cột | cột | -----
    if (/^\s*\|.*\|\s*$/.test(line) &&
        i + 1 < lines.length && /^\s*\|[\s:|-]+\|\s*$/.test(lines[i + 1])) {
      const cells = (row) =>
        row.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim());

      const head = cells(line);
      i += 2;   // bỏ qua dòng phân cách

      const rows = [];
      while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) {
        rows.push(cells(lines[i]));
        i++;
      }

      let table = '<div class="table-wrap"><table><thead><tr>';
      head.forEach((c) => { table += '<th>' + inline(c) + '</th>'; });
      table += '</tr></thead><tbody>';
      rows.forEach((r) => {
        table += '<tr>';
        r.forEach((c) => { table += '<td>' + inline(c) + '</td>'; });
        table += '</tr>';
      });
      table += '</tbody></table></div>';

      out.push(table);
      continue;
    }

    // ----- Trích dẫn: > -----
    if (/^\s*&gt;\s?/.test(line)) {
      const body = [];
      while (i < lines.length && /^\s*&gt;\s?/.test(lines[i])) {
        body.push(lines[i].replace(/^\s*&gt;\s?/, ''));
        i++;
      }
      out.push('<blockquote>' + inline(body.join(' ')) + '</blockquote>');
      continue;
    }

    // ----- Danh sách đánh số -----
    if (/^\s*\d+[.)]\s+/.test(line)) {
      const items = [];
      while (i < lines.length && /^\s*\d+[.)]\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*\d+[.)]\s+/, ''));
        i++;
      }
      out.push('<ol>' + items.map((t) => '<li>' + inline(t) + '</li>').join('') + '</ol>');
      continue;
    }

    // ----- Danh sách gạch đầu dòng -----
    if (/^\s*[-*+]\s+/.test(line)) {
      const items = [];
      while (i < lines.length && /^\s*[-*+]\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*[-*+]\s+/, ''));
        i++;
      }
      out.push('<ul>' + items.map((t) => '<li>' + inline(t) + '</li>').join('') + '</ul>');
      continue;
    }

    // ----- Dòng trống -----
    if (!line.trim()) {
      i++;
      continue;
    }

    // ----- Đoạn văn: gom các dòng liền nhau -----
    const para = [];
    while (i < lines.length &&
           lines[i].trim() &&
           !/^\s*```/.test(lines[i]) &&
           !/^(#{1,6})\s/.test(lines[i]) &&
           !/^\s*[-*+]\s/.test(lines[i]) &&
           !/^\s*\d+[.)]\s/.test(lines[i]) &&
           !/^\s*&gt;\s?/.test(lines[i]) &&
           !/^\s*\|.*\|\s*$/.test(lines[i])) {
      para.push(lines[i]);
      i++;
    }
    if (para.length) {
      out.push('<p>' + inline(para.join('\n')).replace(/\n/g, '<br>') + '</p>');
    }
  }

  return out.join('');
}

/** Gắn sự kiện cho các nút "Sao chép" trong khối code. */
function bindCopyButtons(root) {
  root.querySelectorAll('.code-copy').forEach((btn) => {
    if (btn.dataset.bound) return;
    btn.dataset.bound = '1';

    btn.addEventListener('click', async () => {
      const code = decodeURIComponent(btn.dataset.code || '');
      try {
        await navigator.clipboard.writeText(code);
        btn.textContent = 'Đã chép';
        btn.classList.add('is-done');
      } catch {
        btn.textContent = 'Không chép được';
      }
      setTimeout(() => {
        btn.textContent = 'Sao chép';
        btn.classList.remove('is-done');
      }, 1800);
    });
  });
}
