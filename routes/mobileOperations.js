const express = require('express');
const { requireMobileAuth } = require('../auth');
const {
  listOperationDefinitionsForRole,
  executeOperationRequest
} = require('../operations');

const router = express.Router();

router.get('/', requireMobileAuth, (req, res) => {
  try {
    const role = req.mobileClient?.role || 'viewer';

    res.json({
      ok: true,
      role,
      operations: listOperationDefinitionsForRole(role)
    });
  } catch (err) {
    res.status(500).json({
      ok: false,
      error: err.message
    });
  }
});

router.post('/', requireMobileAuth, async (req, res) => {
  try {
    const result = await executeOperationRequest(req, req.body || {});

    res.status(result.status || (result.ok ? 200 : 400)).json(result);
  } catch (err) {
    res.status(400).json({
      ok: false,
      error: err.message
    });
  }
});

module.exports = router;
