rows = document.getElementsByClassName("iq-table-row iq-clickable")
res = []
for (let r of rows) { const s = new Set(); s.add(r.cells[0].innerText); s.add(r.cells[1].innerText); s.add(r.cells[2].innerText); res.push(s)}
res