function show(type)
{
    count = 0;
    for (var key in methods) {
        var row = document.getElementById(key);
        if ((methods[key] &  type) != 0) {
            row.style.display = '';
            row.className = (count++ % 2) ? rowColor : altColor;
        }
        else
            row.style.display = 'none';
    }
    updateTabs(type);
}

function updateTabs(type)
{
    for (var value in tabs) {
        var sNode = document.getElementById(tabs[value][0]);
        var spanNode = sNode.firstChild;
        if (value == type) {
            sNode.className = activeTableTab;
            spanNode.innerHTML = tabs[value][1];
        }
        else {
            sNode.className = tableTab;
            spanNode.innerHTML = "<a href=\"javascript:show("+ value + ");\">" + tabs[value][1] + "</a>";
        }
    }
}
setTimeout(function() {
    const paramsCandidates = document.querySelectorAll(".blockList .block p")
    const params = Array.prototype.slice.call(paramsCandidates).filter(p => p.textContent.indexOf("param: ") != -1).pop()
    if (params == null) {
        return
    }
    const paramsArray = params.innerText.replace(/\s\s+/g, " ").split("param: ")
    paramsArray.shift()

    const constructorParams = paramsArray.map(param => {
    	const [name, ...rest] = param.split(" ")
    	return {name, description: rest.join(" ")}
    }).map(param => {
        return `<dd class="block"><code>${param.name}</code> &ndash; ${param.description}</dd>`
    })
    constructorParams.unshift("<dd class='block'><span class='paramLabel'>Parameters:</span></dd>")
    const tbody = document.querySelector("[name='constructor.summary'] ~ table tbody")
    const list = document.createElement("dl")
    list.style.marginLeft = "10px"
    list.innerHTML = constructorParams.join("").trim()
    tbody.append(list)

    params.innerHTML = ""
}, 0)
