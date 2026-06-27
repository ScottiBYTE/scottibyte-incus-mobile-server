const express = require('express');
const crypto = require('crypto');
const QRCode = require('qrcode');

const {
  adminSetupRequired,
  createInitialAdminUser,
  getAdminAccessMode,
  verifyAdminCredentials,
  verifyTotpForUser,
  recordAdminLogin
} = require('../adminAuth');

const router = express.Router();

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

router.get('/status', (req, res) => {
  const setupRequired = adminSetupRequired();

  res.json({
    ok: true,
    auth_enabled: !setupRequired,
    setup_required: setupRequired,
    configured: !setupRequired,
    authenticated: Boolean(req.session?.adminAuthenticated),
    username: req.session?.adminUsername || null,
    access_mode: getAdminAccessMode()
  });
});

router.post('/setup', async (req, res) => {
  try {
    if (!adminSetupRequired()) {
      return res.status(409).json({
        ok: false,
        error: 'Admin setup has already been completed'
      });
    }

    const username = String(req.body.username || '').trim();
    const password = String(req.body.password || '');
    const confirmPassword = String(req.body.confirm_password || '');

    if (password !== confirmPassword) {
      return res.status(400).json({
        ok: false,
        error: 'Passwords do not match'
      });
    }

    const result = createInitialAdminUser(username, password);
    const qr_data_url = await QRCode.toDataURL(result.otpauth_url, {
      margin: 2,
      width: 220
    });

    res.json({
      ok: true,
      setup_complete: true,
      username: result.username,
      totp_secret: result.totp_secret,
      otpauth_url: result.otpauth_url,
      qr_data_url
    });
  } catch (err) {
    res.status(400).json({
      ok: false,
      error: err.message
    });
  }
});


router.post('/setup/verify', async (req, res) => {
  try {
    if (adminSetupRequired()) {
      return res.status(428).json({
        ok: false,
        setup_required: true,
        error: 'Initial admin setup required'
      });
    }

    const username = String(req.body.username || '').trim();
    const token = String(req.body.token || '').trim();

    const ok = verifyTotpForUser(username, token);

    if (!ok) {
      await delay(700);
      return res.status(401).json({
        ok: false,
        error: 'Invalid 2FA code'
      });
    }

    res.json({
      ok: true,
      verified: true
    });
  } catch (err) {
    res.status(400).json({
      ok: false,
      error: err.message
    });
  }
});

router.post('/login', async (req, res) => {
  if (adminSetupRequired()) {
    return res.status(428).json({
      ok: false,
      setup_required: true,
      error: 'Initial admin setup required'
    });
  }

  const username = String(req.body.username || '').trim();
  const password = String(req.body.password || '');
  const token = String(req.body.token || '').trim();

  const user = verifyAdminCredentials(username, password, token);

  if (!user) {
    await delay(700);
    return res.status(401).json({
      ok: false,
      error: 'Invalid username, password, or 2FA code'
    });
  }

  req.session.regenerate((err) => {
    if (err) {
      return res.status(500).json({
        ok: false,
        error: 'Unable to create admin session'
      });
    }

    req.session.adminAuthenticated = true;
    req.session.adminUsername = user.username;
    req.session.adminUserId = user.id;
    req.session.adminLoginAt = new Date().toISOString();
    req.session.adminSessionId = crypto.randomBytes(16).toString('hex');

    recordAdminLogin(user.id);

    res.json({
      ok: true,
      authenticated: true,
      username: user.username
    });
  });
});

router.post('/logout', (req, res) => {
  req.session.destroy(() => {
    res.clearCookie('scottibyte.sid');
    res.json({
      ok: true,
      authenticated: false
    });
  });
});

module.exports = router;
