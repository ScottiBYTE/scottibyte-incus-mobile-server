require('dotenv').config();

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const session = require('express-session');
const path = require('path');

const { initDb } = require('./db');
const mobileRoutes = require('./routes/mobile');
const pairingRoutes = require('./routes/pairing');
const adminRoutes = require('./routes/admin');
const adminAuthRoutes = require('./routes/adminAuth');
const { adminAccessGuard } = require('./adminAccess');
const {
  ensureAdminAuthTables,
  getSessionSecret,
  getSessionHours,
  adminSetupRequired,
  requireAdminAuth
} = require('./adminAuth');
const { BetterSqliteSessionStore } = require('./sessionStore');

const app = express();
const PORT = Number(process.env.PORT || 3088);
const TRUST_PROXY = String(process.env.TRUST_PROXY || 'false').toLowerCase() === 'true';

if (TRUST_PROXY) {
  app.set('trust proxy', true);
}

app.use(helmet({ contentSecurityPolicy: false }));
app.use(cors());
app.use(express.json({ limit: '1mb' }));

initDb();
ensureAdminAuthTables();

const sessionHours = getSessionHours();

app.use(session({
  store: new BetterSqliteSessionStore({
    ttlMs: sessionHours * 60 * 60 * 1000
  }),
  name: 'scottibyte.sid',
  secret: getSessionSecret(),
  resave: false,
  saveUninitialized: false,
  cookie: {
    httpOnly: true,
    sameSite: 'lax',
    secure: String(process.env.ADMIN_COOKIE_SECURE || 'false').toLowerCase() === 'true',
    maxAge: sessionHours * 60 * 60 * 1000
  }
}));

app.use('/public', express.static(path.join(__dirname, 'public')));

app.get('/', adminAccessGuard, requireAdminAuth, (req, res) => {
  res.redirect('/admin');
});

app.use('/api/mobile', mobileRoutes);
app.use('/api/pairing', pairingRoutes);

app.use('/api/admin/auth', adminAccessGuard, adminAuthRoutes);
app.use('/api/admin', adminAccessGuard, requireAdminAuth, adminRoutes);

app.get('/admin/setup', adminAccessGuard, (req, res) => {
  if (!adminSetupRequired()) {
    return res.redirect('/admin/login');
  }

  res.sendFile(path.join(__dirname, 'public', 'setup.html'));
});

app.get('/admin/login', adminAccessGuard, (req, res) => {
  if (adminSetupRequired()) {
    return res.redirect('/admin/setup');
  }

  res.sendFile(path.join(__dirname, 'public', 'login.html'));
});

app.get('/admin', adminAccessGuard, requireAdminAuth, (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'admin.html'));
});

app.listen(PORT, () => {
  console.log(`ScottiBYTE Incus Mobile Server listening on port ${PORT}`);
});
