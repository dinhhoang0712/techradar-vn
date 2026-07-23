import { createContext, useContext } from 'react';
import type { MessagingContextValue } from '../types/messaging';

export const MessagingContext = createContext<MessagingContextValue | undefined>(undefined);

export const useMessagingContext = (): MessagingContextValue | undefined => useContext(MessagingContext);
