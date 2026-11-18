// Itonami Agent conversation transport. Every call goes through the shared
// kotoba.app.client-runtime.v1 broker, which asks the person before each
// request; denying sends nothing. This module only declares the operations
// and forwards a request the app already validated. No retry, no fallback,
// no credentials, no other origin.
import { createClientConsentHost, createClientOperationBroker } from "./kotoba-client-runtime.js";

// The owner's Agent conversation service (network-awai/awai-os otehon/bot-chat),
// reached over the owner's tailnet. Same origin and routes as the Awai shell.
export const CHAT_ORIGIN = "https://21mbp.tail110d8b.ts.net:8443";

const op = (operation, path, categories) => ({
  capability: "itonami-bot-chat",
  service: "itonami-muse-conversation",
  operation,
  consent: "each-operation",
  "data-categories": categories,
  endpoint: { origin: CHAT_ORIGIN, routes: [{ method: "POST", path, "query-params": [] }] },
});

const OPERATIONS = {
  "list-bots": op("list-bots", "/v1/bot-chat/bots", ["bot-directory-metadata"]),
  "list-sessions": op("list-sessions", "/v1/bot-chat/sessions", ["bot-identity", "agent-session-metadata"]),
  "create-session": op("create-session", "/v1/bot-chat/sessions", ["bot-identity", "session-title"]),
  "read-history": op("read-history", "/v1/bot-chat/history", ["bot-identity", "agent-conversation-history"]),
  "send-message": op("send-message", "/v1/bot-chat/turn", ["bot-profile", "conversation-message", "agent-conversation-history"]),
};

let consentHost = null;
const brokers = new Map();

// One broker per operation, so each descriptor declares exactly the one
// operation being requested (the Awai shell does the same).
function brokerFor(name) {
  if (!brokers.has(name)) {
    const operation = OPERATIONS[name];
    consentHost ??= createClientConsentHost();
    brokers.set(name, createClientOperationBroker({
      runtime: {
        schema: "kotoba.app.client-runtime.v1",
        target: "browser",
        profile: "trusted-page-v1",
        entry: "index.html",
        "local-state": { adapter: "memory", schema: "itonami.desktop", version: 1 },
        operations: [operation],
      },
      requestedCapabilities: [operation.capability],
      approvedOperations: [operation],
      hostSupportedOperations: [[operation.capability, operation.service, operation.operation]],
      consentHost,
    }));
  }
  return brokers.get(name);
}

// -> {outcome: "ok"|"http"|"denied"|"network", status, body}
async function call(name, path, body) {
  const operation = OPERATIONS[name];
  if (!operation || operation.endpoint.routes[0].path !== path) return { outcome: "invalid" };
  let response;
  try {
    response = await brokerFor(name).invoke(
      { capability: operation.capability, service: operation.service, operation: operation.operation },
      { input: CHAT_ORIGIN + path, init: { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) } },
    );
  } catch (error) {
    const message = typeof error?.message === "string" ? error.message : "";
    if (message.includes("not approved") || message.includes("cancelled")) return { outcome: "denied" };
    return { outcome: "network" };
  }
  let json = null;
  try { json = await response.json(); } catch { json = null; }
  return { outcome: response.ok ? "ok" : "http", status: response.status, body: json };
}

Object.defineProperty(globalThis, "itonamiAgent", {
  value: Object.freeze({ origin: CHAT_ORIGIN, call }),
  writable: false, configurable: false,
});
