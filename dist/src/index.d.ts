import WG from './wireguard';
export default WG;
export declare const ping: () => string;
export declare const connect: (config: string) => Promise<void>;
export declare const disconnect: () => Promise<void>;
export declare const getState: () => Promise<import("../specs/NativeWireGuard").State>;
export type { State } from '../specs/NativeWireGuard';
