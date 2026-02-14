// SoulLogger Dashboard - Frontend Application

(function() {
    'use strict';

    // ==================== State ====================
    const state = {
        theme: localStorage.getItem('theme') || 'light',
        streamConnected: false,
        eventSource: null,
        levelChart: null,
        logCounts: {
            DEBUG: 0,
            INFO: 0,
            WARN: 0,
            ERROR: 0,
            FATAL: 0
        }
    };

    // ==================== DOM Elements ====================
    const elements = {
        themeToggle: document.getElementById('themeToggle'),
        tabBtns: document.querySelectorAll('.tab-btn'),
        tabPanels: document.querySelectorAll('.tab-panel'),
        
        // Stats
        totalLogs: document.getElementById('totalLogs'),
        errorCount: document.getElementById('errorCount'),
        queueSize: document.getElementById('queueSize'),
        statusIcon: document.getElementById('statusIcon'),
        statusText: document.getElementById('statusText'),
        
        // Chart
        levelChart: document.getElementById('levelChart'),
        
        // Logs
        logsList: document.getElementById('logsList'),
        streamToggle: document.getElementById('streamToggle'),
        
        // Config
        levelBtns: document.querySelectorAll('.level-btn'),
        
        // Footer
        connectionStatus: document.getElementById('connectionStatus')
    };

    // ==================== Theme ====================
    function initTheme() {
        document.documentElement.setAttribute('data-theme', state.theme);
    }

    function toggleTheme() {
        state.theme = state.theme === 'light' ? 'dark' : 'light';
        document.documentElement.setAttribute('data-theme', state.theme);
        localStorage.setItem('theme', state.theme);
    }

    // ==================== Tabs ====================
    function initTabs() {
        elements.tabBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                const tabId = btn.dataset.tab;
                
                // Update buttons
                elements.tabBtns.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                
                // Update panels
                elements.tabPanels.forEach(panel => {
                    panel.classList.remove('active');
                    if (panel.id === tabId) {
                        panel.classList.add('active');
                    }
                });
            });
        });
    }

    // ==================== Charts ====================
    function initChart() {
        const ctx = elements.levelChart.getContext('2d');
        
        state.levelChart = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: ['DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'],
                datasets: [{
                    data: [0, 0, 0, 0, 0],
                    backgroundColor: [
                        '#8e8e93',
                        '#007aff',
                        '#ff9500',
                        '#ff3b30',
                        '#af52de'
                    ],
                    borderWidth: 0,
                    hoverOffset: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            padding: 20,
                            usePointStyle: true,
                            pointStyle: 'circle',
                            font: {
                                size: 12,
                                family: '-apple-system, BlinkMacSystemFont, sans-serif'
                            }
                        }
                    }
                },
                cutout: '65%'
            }
        });
    }

    function updateChart() {
        if (state.levelChart) {
            state.levelChart.data.datasets[0].data = [
                state.logCounts.DEBUG,
                state.logCounts.INFO,
                state.logCounts.WARN,
                state.logCounts.ERROR,
                state.logCounts.FATAL
            ];
            state.levelChart.update('none');
        }
    }

    // ==================== Stats ====================
    async function fetchStats() {
        try {
            const response = await fetch('/api/v1/stats');
            const data = await response.json();
            
            // Update total logs
            const total = data.totalLogs || 0;
            elements.totalLogs.textContent = formatNumber(total);
            
            // Update errors
            const errors = (data.logCounts?.ERROR || 0) + (data.logCounts?.FATAL || 0);
            elements.errorCount.textContent = formatNumber(errors);
            
            // Update queue
            elements.queueSize.textContent = formatNumber(data.queueSize || 0);
            
            // Update log counts
            state.logCounts = data.logCounts || state.logCounts;
            updateChart();
            updateAnalysis();
            
            // Update status
            updateStatus(data.active);
            
        } catch (error) {
            console.error('Failed to fetch stats:', error);
            updateStatus(false);
        }
    }

    function updateStatus(active) {
        if (active) {
            elements.statusIcon.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>';
            elements.statusIcon.style.color = 'var(--success)';
            elements.statusText.textContent = 'Active';
        } else {
            elements.statusIcon.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>';
            elements.statusIcon.style.color = 'var(--error)';
            elements.statusText.textContent = 'Inactive';
        }
    }

    function updateAnalysis() {
        const total = Object.values(state.logCounts).reduce((a, b) => a + b, 0) || 1;
        
        document.getElementById('debugCount').textContent = state.logCounts.DEBUG;
        document.getElementById('infoCount').textContent = state.logCounts.INFO;
        document.getElementById('warnCount').textContent = state.logCounts.WARN;
        document.getElementById('errorCountBar').textContent = state.logCounts.ERROR;
        document.getElementById('fatalCount').textContent = state.logCounts.FATAL;
        
        document.getElementById('debugBar').style.width = (state.logCounts.DEBUG / total * 100) + '%';
        document.getElementById('infoBar').style.width = (state.logCounts.INFO / total * 100) + '%';
        document.getElementById('warnBar').style.width = (state.logCounts.WARN / total * 100) + '%';
        document.getElementById('errorBar').style.width = (state.logCounts.ERROR / total * 100) + '%';
        document.getElementById('fatalBar').style.width = (state.logCounts.FATAL / total * 100) + '%';
    }

    // ==================== Log Stream ====================
    function connectStream() {
        if (state.eventSource) {
            state.eventSource.close();
        }
        
        state.eventSource = new EventSource('/api/v1/logs/stream');
        
        state.eventSource.onopen = () => {
            state.streamConnected = true;
            elements.connectionStatus.textContent = 'Connected';
            elements.streamToggle.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>';
        };
        
        state.eventSource.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                addLogEntry(data);
                
                // Update counts
                if (state.logCounts[data.level] !== undefined) {
                    state.logCounts[data.level]++;
                    updateChart();
                    updateAnalysis();
                    
                    // Update total
                    const total = Object.values(state.logCounts).reduce((a, b) => a + b, 0);
                    elements.totalLogs.textContent = formatNumber(total);
                    
                    // Update errors
                    const errors = state.logCounts.ERROR + state.logCounts.FATAL;
                    elements.errorCount.textContent = formatNumber(errors);
                }
            } catch (e) {
                console.error('Failed to parse log:', e);
            }
        };
        
        state.eventSource.onerror = () => {
            state.streamConnected = false;
            elements.connectionStatus.textContent = 'Reconnecting...';
            elements.streamToggle.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>';
            
            // Reconnect after 3 seconds
            setTimeout(connectStream, 3000);
        };
    }

    function addLogEntry(log) {
        const entry = document.createElement('div');
        entry.className = 'log-entry';
        
        const timestamp = formatTimestamp(log.timestamp);
        const level = log.level;
        const command = escapeHtml(log.command);
        
        entry.innerHTML = `
            <span class="timestamp">${timestamp}</span>
            <span class="level ${level.toLowerCase()}">${level}</span>
            <span class="message">${command}</span>
        `;
        
        // Remove empty state
        const empty = elements.logsList.querySelector('.logs-empty');
        if (empty) {
            empty.remove();
        }
        
        // Add to top
        elements.logsList.insertBefore(entry, elements.logsList.firstChild);
        
        // Keep only last 100 entries
        while (elements.logsList.children.length > 100) {
            elements.logsList.removeChild(elements.logsList.lastChild);
        }
    }

    function toggleStream() {
        if (state.streamConnected) {
            state.eventSource.close();
            state.streamConnected = false;
            elements.streamToggle.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>';
        } else {
            connectStream();
        }
    }

    function clearDisplayLogs() {
        elements.logsList.innerHTML = '<div class="logs-empty">Logs cleared</div>';
    }

    // ==================== Config ====================
    async function fetchConfig() {
        try {
            const response = await fetch('/api/v1/config');
            const config = await response.json();
            
            // Update level buttons
            elements.levelBtns.forEach(btn => {
                btn.classList.remove('active');
                if (btn.dataset.level === config.level) {
                    btn.classList.add('active');
                }
            });
            
            // Update toggles
            document.getElementById('rotationToggle').checked = config.rotationEnabled;
            document.getElementById('filterToggle').checked = config.filterEnabled;
            document.getElementById('maskingToggle').checked = config.enableMasking;
            document.getElementById('logbackToggle').checked = config.enableLogback;
            document.getElementById('streamToggle').checked = config.streamEnabled;
            
        } catch (error) {
            console.error('Failed to fetch config:', error);
        }
    }

    async function changeLevel(level) {
        try {
            await fetch('/api/v1/config/level', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ level })
            });
            
            // Update UI
            elements.levelBtns.forEach(btn => {
                btn.classList.remove('active');
                if (btn.dataset.level === level) {
                    btn.classList.add('active');
                }
            });
            
        } catch (error) {
            console.error('Failed to change level:', error);
        }
    }

    // ==================== Actions ====================
    window.generateLogs = async function(level, count) {
        try {
            await fetch('/api/v1/logs/generate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ level, count })
            });
        } catch (error) {
            console.error('Failed to generate logs:', error);
        }
    };

    window.clearLogs = async function() {
        try {
            await fetch('/api/v1/logs/clear', { method: 'POST' });
            
            // Reset state
            state.logCounts = { DEBUG: 0, INFO: 0, WARN: 0, ERROR: 0, FATAL: 0 };
            elements.totalLogs.textContent = '0';
            elements.errorCount.textContent = '0';
            updateChart();
            updateAnalysis();
            clearDisplayLogs();
            
        } catch (error) {
            console.error('Failed to clear logs:', error);
        }
    };

    // ==================== Utilities ====================
    function formatNumber(num) {
        if (num >= 1000000) {
            return (num / 1000000).toFixed(1) + 'M';
        } else if (num >= 1000) {
            return (num / 1000).toFixed(1) + 'K';
        }
        return num.toString();
    }

    function formatTimestamp(timestamp) {
        try {
            const date = new Date(timestamp);
            return date.toLocaleTimeString('en-US', { 
                hour12: false,
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        } catch (e) {
            return timestamp;
        }
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ==================== Event Listeners ====================
    function initEventListeners() {
        // Theme toggle
        elements.themeToggle.addEventListener('click', toggleTheme);
        
        // Stream toggle
        elements.streamToggle.addEventListener('click', toggleStream);
        
        // Level buttons
        elements.levelBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                changeLevel(btn.dataset.level);
            });
        });
    }

    // ==================== Initialize ====================
    function init() {
        initTheme();
        initTabs();
        initChart();
        initEventListeners();
        
        // Initial data fetch
        fetchStats();
        fetchConfig();
        
        // Connect to log stream
        connectStream();
        
        // Periodic stats refresh (backup)
        setInterval(fetchStats, 5000);
    }

    // Start when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
