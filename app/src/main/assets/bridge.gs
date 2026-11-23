/**
 * Groki híd – Google Apps Script webapp.
 * A te Google-fiókodban fut, csak olvas: a legfrissebb Grok "Action Items ... Summary"
 * dokumentumot és az elmúlt 14 nap noreply@x.ai leveleit adja vissza JSON-ként.
 *
 * Telepítés: script.google.com → Új projekt → ezt a kódot bemásolod → Mentés →
 * Telepítés (Deploy) → Új telepítés → Típus: Webalkalmazás →
 * Futtatás mint: Én · Hozzáférés: Bárki → Telepítés → engedélyezés →
 * a kapott /exec végű URL-t bemásolod a Groki appba.
 * Az URL titkos kulcsként működik: ne oszd meg.
 */
function doGet() {
  try {
    return json_({ summary: latestSummary_(), mails: grokMails_() });
  } catch (e) {
    return json_({ error: String(e && e.message || e) });
  }
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function latestSummary_() {
  var since = new Date(Date.now() - 4 * 86400000).toISOString().slice(0, 19);
  var it = DriveApp.searchFiles(
    "title contains 'Action' and mimeType = 'application/vnd.google-apps.document' " +
    "and trashed = false and modifiedDate > '" + since + "'");
  var best = null;
  while (it.hasNext()) {
    var f = it.next();
    var n = f.getName();
    if (!/action[ _]+(items|summary)/i.test(n) || !/summary/i.test(n)) continue;
    if (!best || f.getLastUpdated() > best.getLastUpdated()) best = f;
  }
  if (!best) return null;
  return {
    id: best.getId(),
    name: best.getName(),
    modifiedMs: best.getLastUpdated().getTime(),
    text: docToMarkdown_(best.getId())
  };
}

/** Címsor -> "#", számozott lista 0. szint -> "1. ", alsóbb szint -> "- ". */
function docToMarkdown_(id) {
  var body = DocumentApp.openById(id).getBody();
  var out = [];
  var H = DocumentApp.ParagraphHeading;
  var levels = {};
  levels[H.TITLE] = 1; levels[H.HEADING1] = 1; levels[H.HEADING2] = 2; levels[H.HEADING3] = 3;
  levels[H.HEADING4] = 4; levels[H.HEADING5] = 5; levels[H.HEADING6] = 6;
  for (var i = 0; i < body.getNumChildren(); i++) {
    var el = body.getChild(i);
    var t = el.getType();
    if (t === DocumentApp.ElementType.PARAGRAPH) {
      var p = el.asParagraph(), text = p.getText().trim();
      if (!text) continue;
      var lvl = levels[p.getHeading()];
      out.push(lvl ? new Array(lvl + 1).join('#') + ' ' + text : text);
    } else if (t === DocumentApp.ElementType.LIST_ITEM) {
      var li = el.asListItem(), lt = li.getText().trim();
      if (!lt) continue;
      out.push(li.getNestingLevel() === 0 ? '1. ' + lt : '  - ' + lt);
    }
  }
  return out.join('\n');
}

function grokMails_() {
  var threads = GmailApp.search('from:noreply@x.ai newer_than:14d', 0, 40);
  var res = [];
  threads.forEach(function (th) {
    var msgs = th.getMessages();
    var m = msgs[msgs.length - 1];
    res.push({
      id: m.getId(),
      threadId: th.getId(),
      subject: m.getSubject() || '',
      snippet: (m.getPlainBody() || '').slice(0, 600),
      dateMs: m.getDate().getTime(),
      unread: m.isUnread()
    });
  });
  return res;
}
