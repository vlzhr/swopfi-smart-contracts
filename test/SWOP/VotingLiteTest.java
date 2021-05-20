package SWOP;

import com.wavesplatform.crypto.Crypto;
import com.wavesplatform.transactions.DataTransaction;
import com.wavesplatform.transactions.account.PrivateKey;
import dapps.GovernanceDApp;
import dapps.VotingDApp;
import im.mak.paddle.Account;
import im.mak.paddle.DataParams;
import im.mak.paddle.api.TxInfo;
import im.mak.paddle.exceptions.ApiError;
import com.wavesplatform.transactions.common.AssetId;
import com.wavesplatform.transactions.data.IntegerEntry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static im.mak.paddle.token.Waves.WAVES;
import static im.mak.paddle.util.Async.async;
import static im.mak.paddle.Node.node;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Alphanumeric.class)
public class VotingLiteTest {
    static final long firstCallerInitAmount = 1000_00000000L;
    static final long secondCallerInitAmount = 1000_00000000L;
    static final int fullDuration = 1443;
    static final int periodLength = 5;
    static final String keyRewardPoolFractionCurrent = "_current_pool_fraction_reward";
    static final String keyRewardPoolFractionPrevious = "_previous_pool_fraction_reward";
    static final String keyRewardUpdateHeight = "reward_update_height";

    static final String kUserPoolVoteSWOP = "_vote";
    static final String kUserPoolStruc = "_user_pool_struc";
    static final String kUserTotalVoteSWOP = "_user_total_SWOP_vote";
    static final String kUserTotalStruc = "_user_total_struc";
    static final String kPoolVoteSWOP = "_vote_SWOP";
    static final String kPoolStruc = "_pool_struc";
    static final String kTotalVoteSWOP = "total_vote_SWOP";
    static final String kTotalStruc = "total_struc";
    static final String kHarvestPoolActiveVoteStruc = "_harvest_pool_activeVote_struc";
    static final String kHarvestUserPoolActiveVoteStruc = "_harvest_user_pool_activeVote_struc";
    static final String kStartHeight = "start_height";
    static final String kBasePeriod = "base_period";
    static final String kPeriodLength = "period_length";
    static final String kDurationFullVotePower = "duration_full_vote_power";
    static final String kMinVotePower = "min_vote_power";

    static final String fakePool = "3P5N94Qdb8SqJuy5fp1btfzz1zACpPbqs6x";
    static final String firstPool = "3P5N94Qdb8SqJuy56p1btfzz1zACpPbqs6x";
    static final String secondPool = "3PA26XNQfUzwNQHhSEbtKzRfYFvAcgj2Nfw";
    static final String thirdPool = "3PLZSEaGDLht8GGK8rDfbY8zraHcXYHeiwP";
    static final String fourthPool = "3P4D2zZJubRPbFTurHpCNS9HbFaNiw6mf7D";
    static final String fifthPool = "3PPRh8DHaVTPqiv1Mes5amXq3Dujg7wSjZm";

    static Account firstCaller, farming;
    static VotingDApp voting;
    static GovernanceDApp governance;
    static AssetId swopId;

    @BeforeEach
    void before() {
        PrivateKey votingPK = PrivateKey.fromSeed(Crypto.getRandomSeedBytes());
        PrivateKey governancePK = PrivateKey.fromSeed(Crypto.getRandomSeedBytes());

        async(
                () -> firstCaller = new Account(WAVES.amount(1000)),
                () -> farming = new Account(WAVES.amount(1000))
        );

        swopId = firstCaller.issue(a -> a.quantity(Long.MAX_VALUE).name("SWOP").decimals(8)).tx().assetId();
        async(
                () -> voting = new VotingDApp(votingPK, WAVES.amount(100), governancePK.address()),
                () -> governance = new GovernanceDApp(governancePK, WAVES.amount(1000), farming.publicKey(), votingPK.address())
        );
        async(
                () -> farming.writeData(d -> d.string("SWOP_id", swopId.toString()))
        );
        async(
                () -> governance.writeData(d -> d
                        .integer(firstPool + keyRewardPoolFractionCurrent, 20_00000000L)
                        .integer(firstPool + keyRewardPoolFractionPrevious, 20_00000000L)
                        .integer(secondPool + keyRewardPoolFractionCurrent, 20_00000000L)
                        .integer(secondPool + keyRewardPoolFractionPrevious, 20_00000000L)
                        .integer(thirdPool + keyRewardPoolFractionCurrent, 10_00000000L)
                        .integer(thirdPool + keyRewardPoolFractionPrevious, 10_000000000L)
                        .integer(fourthPool + keyRewardPoolFractionCurrent, 35_00000000L)
                        .integer(fourthPool + keyRewardPoolFractionPrevious, 35_000000000L)
                        .integer(fifthPool + keyRewardPoolFractionCurrent, 15_00000000L)
                        .integer(fifthPool + keyRewardPoolFractionPrevious, 15_000000000L)
                        .integer(keyRewardUpdateHeight, node().getHeight())),
                () -> voting.writeData(d -> d
                        .integer(kBasePeriod, 0)
                        .integer(kPeriodLength, periodLength)
                        .integer(kStartHeight, node().getHeight())
                        .integer(kDurationFullVotePower, fullDuration)
                        .integer(kMinVotePower, 10000000))
        );
    }

    void initState(String pool, int dateTransaction, long stakedSwop,
                   String initUserPool, String initUserTotal, String initPool, String initTotal) {
        final String finalUserPool = initUserPool != null && initUserPool.contains("_") ? kUserPoolStruc : kUserPoolVoteSWOP;
        final String finalUserTotal = initUserTotal != null && initUserTotal.contains("_") ? kUserTotalStruc : kUserTotalVoteSWOP;
        final String finalPool = initPool.contains("_") ? kPoolStruc : kPoolVoteSWOP;
        final String finalTotal = initTotal.contains("_") ? kTotalStruc : kTotalVoteSWOP;


        firstCaller.transfer(governance, stakedSwop, swopId);
        governance.writeData(d -> d
                .integer(firstCaller.address().toString() + "_SWOP_amount", stakedSwop));

        if (initUserPool == null) {
        } else if (!initUserPool.contains("_"))
            voting.writeData(d -> d.integer(firstCaller.address().toString() + "_" + pool + finalUserPool, new Long(initUserPool)));
        else
            voting.writeData(d -> d.string(firstCaller.address().toString() + "_" + pool + finalUserPool, initUserPool));

        if (initUserTotal == null) {
        } else if (!initUserTotal.contains("_"))
            voting.writeData(d -> d.integer(firstCaller.address().toString() + finalUserTotal, new Long(initUserTotal)));
        else voting.writeData(d -> d.string(firstCaller.address().toString() + finalUserTotal, initUserTotal));

        if (!initPool.contains("_")) voting.writeData(d -> d.integer(pool + finalPool, new Long(initPool)));
        else voting.writeData(d -> d.string(pool + finalPool, initPool));

        if (!initTotal.contains("_")) voting.writeData(d -> d.integer(finalTotal, new Long(initTotal)));
        else voting.writeData(d -> d.string(finalTotal, initTotal));

        node().waitNBlocks(dateTransaction);
    }

    static Stream<Arguments> initAndResultStateProvider() {
        return Stream.of(
                Arguments.of(true, firstPool, 0, 1L, 1_00000000L, 0,
                        "1200", "123000", "12340000", "1234500000", null,
                        100, "1_1_1_1200", "121801_1199_1", "12338801_12338801_1", "1234498801_1234498801_1"),
                Arguments.of(true, firstPool, 0, 1L, 1_00000000L, 0,
                        "1200", "123000", "12340000", "1234500000", null,
                        100, "1_1_1_1200", "121801_1199_1", "12338801_12338801_1", "1234498801_1234498801_1"),
                Arguments.of(true, firstPool, 0, 1L, 1_00000000L, 0,
                        "1200", "123000", "12340000", "1234500000", null,
                        100, "1_1_1_1200", "121801_1199_1", "12338801_12338801_1", "1234498801_1234498801_1"),
                Arguments.of(true, firstPool, 0, 1L, 1_00000000L, 0,
                        "1200", "123000", "12340000", "1234500000", null,
                        100, "1_1_1_1200", "121801_1199_1", "12338801_12338801_1", "1234498801_1234498801_1"),
                Arguments.of(true, firstPool, 0, 1L, 1_00000000L, 1,
                        null, null, "12340000_12340000_0", "1234500000_1234500000_0", null,
                        100, "1_1_1_1200", "1_1199_1", "12340001_12340001_1", "1234500001_1234500001_1"),
                Arguments.of(true, firstPool, 0, 1L, 1_00000000L, 1,
                        "1200_1200_0_1200", "123000_0_0", "12340000_12340000_0", "1234500000_1234500000_0", null,
                        100, "1_1_1_1200", "121801_1199_1", "12338801_12338801_1", "1234498801_1234498801_1"),
                Arguments.of(true, firstPool, 0, 0L, 1_00000000L, 1,
                        "1200_1200_1_1200", "123000_0_1", "12340000_12340000_1", "1234500000_1234500000_1", null,
                        100, "0_0_1_1200", "121801_1200_1", "12338800_12338801_1", "1234498800_1234498801_1"),
                Arguments.of(false, fakePool, 0, 1L, 1_00000000L, 1,
                        "1200_1200_0_1200", "123000_0_0", "12340000_12340000_0", "1234500000_1234500000_0", null,
                        100, "0_0_0_0", "121801_0_0", "12338800_12338801_0", "1234498800_1234498801_0"),
                Arguments.of(false, fakePool, 0, -1L, 1_00000000L, 1,
                        "1200_1200_0_1200", "123000_0_0", "12340000_12340000_0", "1234500000_1234500000_0", null,
                        100, "0_0_0_0", "121801_0_0", "12338800_12338801_0", "1234498800_1234498801_0"),
                Arguments.of(true, firstPool, 0, 100000_00000000L, 100000_00000000L, 1,
                        "1200_1200_0_1200", "123000_0_0", "12340000_12340000_0", "1234500000_1234500000_0", null,
                        100, "10000000001200_10000000001200_1_10000100001200", "10000000123000_10000000123000_1", "10000012340000_10000012340000_1", "10001234500000_10001234500000_1"),
                Arguments.of(true, firstPool, 0, 100001_00000000L, 100001_00000000L, 1,
                        "10000000001200_10000000001200_1_10", "10000000123000_10000000123000_1", "10000012340000_10000012340000_1", "10001234500000_10001234500000_1", null,
                        100, "10000100001200_10000100001200_1_10000100001200", "10000100123000_10000100123000_1", "10000112340000_10000112340000_1", "10001334500000_10001334500000_1"),
                Arguments.of(true, firstPool, 0, 1_00000000L, 100_00000000L, fullDuration-1,
                        null, null, "12340000_12340000_1", "1234500000_1234500000_1", null,
                        100, "100000000_100000000_1_100000000", "100000000_0_1", "1012340000_1012340000_1", "11234500000_11234500000_0"),
                Arguments.of(true, firstPool, 0, 1_00000000L, 100_00000000L, fullDuration,
                        null, null, "12340000_12340000_1", "1234500000_1234500000_1", null,
                        100, "10000000_99999987_1_10000000", "99999987_0_1", "1012340000_1012339987_1", "11234500000_11234499987_1"),
                Arguments.of(true, firstPool, 0, 1_00000000L, 100_00000000L, fullDuration,
                        "1200_1200_1_1200", "123000_0_1", "12340000_12340000_1", "1234500000_1234500000_1", null,
                        100, "10000000_99999989_1_10000000", "100000000_0_1", "1012340000_1012339989_1", "11234500000_11234499989_1"),
                Arguments.of(true, firstPool, 0, 1_00000000L, 100_00000000L, fullDuration-1,
                        null, null, "12340000_12340000_1", "1234500000_1234500000_1", null,
                        100, "10000000_1000000_1_10000000", "10000000_0_1", "112340000_22340000_1", "1334500000_1244500000_1"),
                Arguments.of(true, firstPool, 0, 1_00000000L, 100_00000000L, fullDuration-1,
                        "1200_1200_1_1200", "123000_0_1", "12340000_12340000_1", "1234500000_1234500000_1", null,
                        100, "100000000_10001080_1_100000000", "20000000_0_1", "112338800_22339880_1", "1334498800_1244499880_1")
        );
    }

    @ParameterizedTest(name = "{index} {1}")
    @MethodSource("initAndResultStateProvider")
    void firstVote(boolean statusOfTransaction, String poolAddresses, int periodId, long poolsVoteSWOPNew, long stakedSwop, int dateTransaction,
                   String initUserPool, String initUserTotal, String initPool, String initTotal, String dataTransOther,
                   int votingPower, String resultUserPool, String resultUserTotal, String resultPool, String resultTotal) {
        node().waitNBlocks(periodLength);
        boolean successTx = true;
        initState(poolAddresses, dateTransaction, stakedSwop, initUserPool, initUserTotal, initPool, initTotal);

        try {
            firstCaller.invoke(voting.votePoolWeight(singletonList(poolAddresses), singletonList(poolsVoteSWOPNew)));
        } catch (Exception e) {
            e.printStackTrace();
            successTx = false;
        }

        if (statusOfTransaction) {
            boolean finalSuccessTx = successTx;
            assertAll("vote pool weight",
                    () -> assertThat(resultUserPool).isEqualTo(voting.getStringData(firstCaller.address() + "_" + poolAddresses + kUserPoolStruc)),
                    () -> assertThat(resultUserTotal).isEqualTo(voting.getStringData(firstCaller.address() + kUserTotalStruc)),
                    () -> assertThat(resultPool).isEqualTo(voting.getStringData(poolAddresses + kPoolStruc)),
                    () -> assertThat(resultTotal).isEqualTo(voting.getStringData(kTotalStruc)),
                    () -> assertTrue(finalSuccessTx));
        } else
            assertFalse(successTx);
    }
}