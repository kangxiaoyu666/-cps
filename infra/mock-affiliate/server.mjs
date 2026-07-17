import { createServer } from "node:http";
import { URL } from "node:url";

const port = Number(process.env.PORT || 8090);
const state = new Map();

function json(response, status, body) {
  response.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(body));
}

async function body(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  const raw = Buffer.concat(chunks).toString("utf8");
  if (!raw) return {};
  if (request.headers["content-type"]?.includes("application/x-www-form-urlencoded")) {
    return Object.fromEntries(new URLSearchParams(raw));
  }
  return JSON.parse(raw);
}

function order(platform, id) {
  const key = `${platform}:${id}`;
  if (!state.has(key)) {
    state.set(key, {
      id,
      platform,
      status: "PAID",
      sid: "u1",
      amount: "35.80",
      commission: "3.58",
      refundCommission: "0.00"
    });
  }
  return state.get(key);
}

function meituanOrder(item) {
  const status = { PAID: "2", SETTLED: "6", REFUNDED: "4" }[item.status] || "2";
  return {
    businessLine: 1,
    orderId: item.id,
    payTime: Math.floor(Date.now() / 1000) - 60,
    updateTime: Math.floor(Date.now() / 1000),
    payPrice: item.amount,
    profit: item.commission,
    refundProfit: item.refundCommission,
    refundTime: item.status === "REFUNDED" ? Math.floor(Date.now() / 1000) : null,
    status,
    sid: item.sid,
    actId: "mock-act"
  };
}

function topResponse(method, payload) {
  const root = `${method.replaceAll(".", "_")}_response`;
  if (method.includes("officialactivity")) {
    return { [root]: { result_success: true, result_code: 0, data: { link: { h5_short_link: "http://localhost:8090/mock-eleme" } } } };
  }
  const items = [...state.values()].filter((item) => item.platform === "ELEME");
  if (method.includes("refund")) {
    const refunds = items.filter((item) => item.status === "REFUNDED").map((item) => ({
      attr_type: "0",
      biz_order_id: item.id,
      pay_amount: item.amount,
      refund_amount: item.amount,
      explain_state: 0,
      return_commission_state: 1,
      explain_end_time: "2026-07-17 10:00:00",
      gmt_modified: "2026-07-17 10:00:00",
      sid: item.sid,
      pid: "mock-pid"
    }));
    return { [root]: { result_success: true, result: { refund_order_detail_report_d_t_o: refunds }, total_count: refunds.length } };
  }
  const orders = items.map((item) => ({
    attr_type: "0",
    biz_order_id: item.id,
    pay_amount: item.amount,
    income: item.commission,
    settle: item.status === "SETTLED" ? item.commission : "0.00",
    pay_time: "2026-07-17 09:00:00",
    settle_time: item.status === "SETTLED" ? "2026-07-17 10:00:00" : null,
    gmt_modified: "2026-07-17 10:00:00",
    order_state: item.status === "PAID" ? 2 : 4,
    order_item_status: item.status === "REFUNDED" ? 3 : 2,
    settle_state: item.status === "SETTLED" ? 1 : 0,
    sid: item.sid,
    pid: "mock-pid",
    activity_id: "mock-act"
  }));
  return { [root]: { result_success: true, result: { order_detail_report_d_t_o: orders }, total_count: orders.length } };
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url || "/", `http://${request.headers.host}`);
  if (request.method === "GET" && url.pathname === "/health") return json(response, 200, { status: "UP" });
  if (request.method === "POST" && url.pathname === "/mock/orders") {
    const input = await body(request);
    const item = order(String(input.platform || "MEITUAN").toUpperCase(), String(input.orderId || `MOCK-${Date.now()}`));
    Object.assign(item, input);
    state.set(`${item.platform}:${item.id}`, item);
    return json(response, 200, item);
  }
  if (request.method === "GET" && url.pathname === "/mock/orders") return json(response, 200, [...state.values()]);
  if (request.method === "POST" && url.pathname.endsWith("/get_referral_link")) {
    const input = await body(request);
    return json(response, 200, { code: 0, data: `http://localhost:${port}/mock-meituan?sid=${encodeURIComponent(input.sid || "validation")}` });
  }
  if (request.method === "POST" && url.pathname.endsWith("/query_order")) {
    const input = await body(request);
    const items = input.orderId
      ? [order("MEITUAN", String(input.orderId))]
      : [...state.values()].filter((item) => item.platform === "MEITUAN");
    return json(response, 200, { code: 0, data: { dataList: items.map(meituanOrder), scrollId: "" } });
  }
  if (request.method === "POST" && url.pathname === "/router/rest") {
    const input = await body(request);
    return json(response, 200, topResponse(String(input.method || ""), input));
  }
  if (request.method === "GET" && (url.pathname === "/mock-meituan" || url.pathname === "/mock-eleme")) {
    response.writeHead(200, { "Content-Type": "text/plain; charset=utf-8" });
    return response.end("本地联盟 Mock 推广链接已打开");
  }
  return json(response, 404, { code: "NOT_FOUND", message: "Mock endpoint not found" });
});

server.listen(port, "0.0.0.0", () => {
  process.stdout.write(`affiliate mock listening on ${port}\n`);
});
