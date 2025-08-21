import type { TurboModule } from 'react-native';
export type State = 'DOWN' | 'UP' | 'ERROR' | 'UNKNOWN';
export interface Spec extends TurboModule {
    ping(): string;
    connect(config: string): Promise<void>;
    disconnect(): Promise<void>;
    getState(): Promise<State>;
}
declare const _default: Spec;
export default _default;
