package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/*
Description:
Complete an order checkout process and deduct the basket total from the user's wallet balance.
End users should only be able to checkout their own baskets.
Internal users such as other internal services can checkout any basket
and deduct from the basket owner's wallet.
*/

@RequestMapping("/v3/baskets/")
@RestController
public class CheckoutController {

    public static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    @Autowired
    public WalletService walletService;
    @Autowired
    public BasketRepository basketRepository;

    @PutMapping(value = "/new/{basketId}/v2/checkout/confirm")
    public String checkoutBasket(@PathVariable String basketid,
                                 @RequestParam boolean isInternal,
                                 Authentication auth,
                                 @RequestParam String walletId) {

        boolean canAccess = true;
        if (!isInternal || auth.getName() != null) {
            List<Basket> bList = basketRepository.findByUsername(auth.getName());
            boolean ownsBasket = false;
            bList.stream().forEach(b -> {
                if (b.getId().equals(basketid)) {
                    ownsBasket = true;
                }
            });
            if (!ownsBasket) canAccess = false;
        }


        if (!canAccess) return "NOT-ALLOWED";

        if (walletId == null) {
            var basketOptional = basketRepository.findById(basketid);
            walletId = basketOptional.get().getUserWallet();
        }

        Wallet wallet = walletService.getWallet(walletId);
        Optional.ofNullable(wallet).orElseThrow();
        double balance = wallet.getBalance();

        var basketOptional = basketRepository.findById(basketid);
        if (balance > basketOptional.get().getTotal()) {
            basketRepository.changeStatus(basketid, "SEND_FOR_DELIVERY");
            walletService.reduceBalance(walletId, basketOptional.get().getTotal());
        } else {
            return "INSUFFICIENT_FUNDS";
        }

        return "OK";

    }
}