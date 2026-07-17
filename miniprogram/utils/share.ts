import { ShareScene } from "./api-types";
import { request } from "./request";

let scenePromise: Promise<ShareScene> | null = null;

export function createShareScene(): Promise<ShareScene> {
  if (!scenePromise) {
    scenePromise = request<ShareScene>("/mini/share-scenes", { method: "POST" })
      .finally(() => { scenePromise = null; });
  }
  return scenePromise;
}

export function sharePath(pagePath: string, scene: string): string {
  return `${pagePath}?scene=${encodeURIComponent(scene)}`;
}
