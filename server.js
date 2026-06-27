require('dotenv').config();

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const path = require('path');

const { initDb } = require('./db');
const mobileRoutes = require('./routes/mobile');
const pairingRoutes = require('./routes/pairing');
const adminRoutes = require('./routes/admin');
const { adminAccessGuard } = require('./adminAccess');

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

app.use('/public', express.static(path.join(__dirname, 'public')));

app.get('/', adminAccessGuard, (req, res) => {
  res.redirect('/admin');
});

app.use('/api/mobile', mobileRoutes);
app.use('/api/pairing', pairingRoutes);
app.use('/api/admin', adminAccessGuard, adminRoutes);

app.get('/admin', adminAccessGuard, (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'admin.html'));
});

app.listen(PORT, () => {
  console.log(`ScottiBYTE Incus Mobile Server listening on port ${PORT}`);
});
