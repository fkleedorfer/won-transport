package won.transport.taxi.bot.action;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.command.connectionmessage.ConnectionMessageCommandEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.MessageFromOtherNeedEvent;
import won.bot.framework.eventbot.listener.EventListener;
import won.protocol.highlevel.HighlevelProtocols;
import won.protocol.model.Connection;
import won.protocol.util.WonRdfUtils;
import won.protocol.util.linkeddata.WonLinkedDataUtils;
import won.transport.taxi.bot.client.entity.Parameter.OrderState;
import won.transport.taxi.bot.client.entity.Parameter.OrderStateMessage;
import won.transport.taxi.bot.client.entity.Parameter.Parameter;
import won.transport.taxi.bot.client.entity.Result;
import won.transport.taxi.bot.impl.TaxiBotContextWrapper;

import java.net.URI;
import java.util.Iterator;

/**
 * Created by fsuda on 02.03.2018.
 */
public class ControlMessageAction extends BaseEventBotAction {
    public ControlMessageAction(EventListenerContext eventListenerContext) {
        super(eventListenerContext);
    }

    @Override
    protected void doRun(Event event, EventListener executingListener) throws Exception {
        EventListenerContext ctx = getEventListenerContext();

        if(ctx.getBotContextWrapper() instanceof TaxiBotContextWrapper &&  event instanceof MessageFromOtherNeedEvent){
            Connection con = ((MessageFromOtherNeedEvent) event).getCon();
            TaxiBotContextWrapper taxiBotContextWrapper = (TaxiBotContextWrapper) ctx.getBotContextWrapper();
            MessageFromOtherNeedEvent messageFromOtherNeedEvent = (MessageFromOtherNeedEvent) event;

            String textMessage = WonRdfUtils.MessageUtils.getTextMessage(messageFromOtherNeedEvent.getWonMessage());

            if ("status".equals(textMessage)) {
                publishAnalyzingMessage(con);

                Dataset fullConversationDataset = WonLinkedDataUtils.getConversationAndNeedsDataset(con.getConnectionURI(), ctx.getLinkedDataSource());
                Dataset presentAgreements = HighlevelProtocols.getAgreements(fullConversationDataset);

                if(presentAgreements.isEmpty()){
                    ctx.getEventBus().publish(new ConnectionMessageCommandEvent(con, WonRdfUtils.MessageUtils.textMessage("No Agreements Present for your connection")));
                }else{
                    Iterator<String> presentAgreementUris = presentAgreements.listNames();

                    while(presentAgreementUris.hasNext()){
                        String agreementUri = presentAgreementUris.next();
                        String orderId = taxiBotContextWrapper.getOfferIdForAgreementURI(URI.create(agreementUri));

                        Result orderState = taxiBotContextWrapper.getMobileBooking().getOrderState(orderId);
                        String statusMessage = "No Status Available";

                        if(orderState.getError() == null) {
                            for (Parameter param : orderState.getParameter()) {
                                if (param instanceof OrderStateMessage) {
                                    statusMessage = ((OrderStateMessage) param).getValue();
                                }
                            }
                        }else{
                            statusMessage = orderState.getError().getText();
                        }
                        //TODO: ParseMessage better
                        ctx.getEventBus().publish(new ConnectionMessageCommandEvent(con, WonRdfUtils.MessageUtils.textMessage("Agreement: <"+agreementUri+"> has the orderId: "+orderId+" with status: '"+statusMessage+"'")));
                    }
                }
            }
        }
    }

    private void publishAnalyzingMessage(Connection connection) {
        Model messageModel = WonRdfUtils.MessageUtils.textMessage("Calculating your Orderstatus...");
        getEventListenerContext().getEventBus().publish(new ConnectionMessageCommandEvent(connection, messageModel)); //TODO: REMOVE THIS OR CHANGE IT TO A SORT-OF PROCESSING MESSAGE TYPE
    }
}
