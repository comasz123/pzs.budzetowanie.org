(function () {
    function parseMoney(value) {
        if (value == null || value === '') {
            return 0;
        }
        let text = String(value).trim().replace(/\u00a0|\u202f/g, '').replace(/ /g, '');
        if (text === '' || text === '-') {
            return 0;
        }
        if (text.includes(',') && text.includes('.')) {
            text = text.replace(/\./g, '').replace(',', '.');
        } else {
            text = text.replace(',', '.');
        }
        const parsed = Number(text);
        return Number.isFinite(parsed) ? parsed : 0;
    }

    function formatMoney(value) {
        const amount = typeof value === 'number' ? value : parseMoney(value);
        if (!Number.isFinite(amount)) {
            return '';
        }
        return amount.toLocaleString('pl-PL', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
            useGrouping: true,
            minimumGroupingDigits: 1
        }).replace(/\u00a0|\u202f/g, ' ');
    }

    function commitMoneyInput(input) {
        const source = Object.prototype.hasOwnProperty.call(input.dataset, 'moneyDraft')
            ? input.dataset.moneyDraft
            : input.value;
        if (String(source).trim() === '') {
            input.value = '';
            input.defaultValue = '';
            return;
        }
        const formatted = formatMoney(source);
        input.value = formatted;
        input.defaultValue = formatted;
        input.dataset.moneyDraft = formatted;
    }

    function silenceSuggestions(input) {
        if (input.dataset.suggestionsSilenced === '1') {
            return;
        }
        input.dataset.suggestionsSilenced = '1';
        input.setAttribute('autocomplete', 'off');
        input.setAttribute('autocorrect', 'off');
        input.setAttribute('autocapitalize', 'off');
        input.setAttribute('spellcheck', 'false');
        input.setAttribute('readonly', 'readonly');
        input.addEventListener('pointerdown', (event) => {
            if (!input.hasAttribute('readonly') || input.disabled) {
                return;
            }
            event.preventDefault();
            input.removeAttribute('readonly');
            input.focus();
        });
        input.addEventListener('input', () => {
            input.dataset.moneyDraft = input.value;
        });
        input.addEventListener('focus', () => {
            input.removeAttribute('readonly');
        });
        input.addEventListener('blur', () => {
            commitMoneyInput(input);
            if (!input.disabled) {
                input.setAttribute('readonly', 'readonly');
            }
            const keep = input.value;
            requestAnimationFrame(() => {
                if (input.value !== keep) {
                    input.value = keep;
                    input.defaultValue = keep;
                }
            });
        });
    }

    function formatMoneyInputs(root) {
        (root || document).querySelectorAll('input.money-input').forEach((input) => {
            silenceSuggestions(input);
            if (input.value.trim() === '') {
                return;
            }
            input.value = formatMoney(input.value);
        });
    }

    document.addEventListener('focusout', (event) => {
        const input = event.target;
        if (!(input instanceof HTMLInputElement) || !input.classList.contains('money-input')) {
            return;
        }
        commitMoneyInput(input);
    });

    document.addEventListener('submit', (event) => {
        const form = event.target;
        if (!(form instanceof HTMLFormElement)) {
            return;
        }
        form.querySelectorAll('input.money-input').forEach((input) => {
            input.removeAttribute('readonly');
            commitMoneyInput(input);
        });
    }, true);

    window.parseMoney = parseMoney;
    window.formatMoney = formatMoney;
    window.formatMoneyInputs = formatMoneyInputs;

    function watchMoneyInputs() {
        formatMoneyInputs(document);
        const observer = new MutationObserver((records) => {
            records.forEach((record) => {
                record.addedNodes.forEach((node) => {
                    if (!(node instanceof Element)) {
                        return;
                    }
                    if (node.matches('input.money-input')) {
                        silenceSuggestions(node);
                    }
                    formatMoneyInputs(node);
                });
            });
        });
        observer.observe(document.documentElement, {childList: true, subtree: true});
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', watchMoneyInputs);
    } else {
        watchMoneyInputs();
    }
})();
