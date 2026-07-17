export type AffiliatePlatform = "MEITUAN" | "ELEME";

export interface PromotionLink {
  platform: AffiliatePlatform;
  url: string;
  expiresAt: string | null;
}

export interface PromotionLinkRequest {
  platform: AffiliatePlatform;
  activityCode: string;
}

export interface Withdrawal {
  id: number;
  withdrawal_no: string;
  amount_cent: number;
  status: string;
  submitted_at: string;
  paid_at: string | null;
}

export interface WithdrawalRequest {
  amountCent: number;
}

export interface ShareScene {
  scene: string;
  expiresIn: number;
}
